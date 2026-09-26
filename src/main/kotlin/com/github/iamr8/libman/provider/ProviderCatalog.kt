package com.github.iamr8.libman.provider

import com.github.iamr8.libman.util.HttpValidators
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.util.io.HttpRequests
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Resolves a library's versions + description from its provider, and the URL of its page on that
 * provider. Network here; call off the EDT. Parsing is delegated to [CatalogParsers] (unit-tested).
 *
 * Provider mapping:
 *  - cdnjs (default)      -> api.cdnjs.com (versions + description)
 *  - unpkg               -> registry.npmjs.org (versions + description)
 *  - jsdelivr, npm form   -> registry.npmjs.org (versions + description)
 *  - jsdelivr, gh form    -> data.jsdelivr.com gh API (versions only)
 *  - filesystem           -> no catalog (a local path has no versions)
 */
object ProviderCatalog {

    private const val TIMEOUT_MS = 15_000
    private const val SEARCH_LIMIT = 20

    fun isSupported(provider: String?): Boolean = normalize(provider) != "filesystem"

    /**
     * Fetches versions + description, or why that failed (network, HTTP status, bad payload).
     * With [validators] from an earlier response, the request is conditional, and an unchanged
     * catalog comes back as [CatalogFetch.NotModified] (no body downloaded).
     */
    fun fetch(provider: String?, name: String, validators: HttpValidators?): CatalogFetch {
        val p = normalize(provider)
        val url = when (p) {
            "filesystem" -> return CatalogFetch.Failed("the filesystem provider has no catalog")
            "unpkg" -> npmUrl(name)
            "jsdelivr" -> if (isGitHubForm(name)) jsdelivrGhUrl(name) else npmUrl(name)
            else -> cdnjsUrl(name) // cdnjs + unknown default to cdnjs
        }
        val response = try {
            HttpRequests.request(url)
                .accept("application/json")
                .connectTimeout(TIMEOUT_MS)
                .readTimeout(TIMEOUT_MS)
                .tuner { c -> validators?.requestHeaders()?.forEach { (k, v) -> c.setRequestProperty(k, v) } }
                .connect { request ->
                    // HttpRequests returns a 304 as a normal connection (no exception, empty body).
                    val http = request.connection as? HttpURLConnection
                    if (http?.responseCode == HttpURLConnection.HTTP_NOT_MODIFIED) return@connect null
                    Response(
                        // Pass the running task's indicator so the download honors cancel; the read loop
                        // only checks cancellation when the indicator is non-null. It is null off a task.
                        request.readString(ProgressManager.getInstance().progressIndicator),
                        HttpValidators.of(http?.getHeaderField("ETag"), http?.getHeaderField("Last-Modified")),
                    )
                }
        } catch (e: ProcessCanceledException) {
            throw e // cancellation must propagate so the task stops and is not logged as a failure
        } catch (e: HttpRequests.HttpStatusException) {
            return CatalogFetch.Failed(FetchFailures.forStatus(e.statusCode))
        } catch (e: Exception) {
            return CatalogFetch.Failed(FetchFailures.describe(e))
        }
        if (response == null) return CatalogFetch.NotModified
        val body = response.body
        val info = when (p) {
            "unpkg" -> CatalogParsers.parseNpm(body)
            "jsdelivr" -> if (isGitHubForm(name)) CatalogParsers.parseJsdelivr(body) else CatalogParsers.parseNpm(body)
            else -> CatalogParsers.parseCdnjs(body)
        }
        return info?.let { CatalogFetch.Found(it, response.validators) }
            ?: CatalogFetch.Failed(FetchFailures.UNEXPECTED_RESPONSE)
    }

    private class Response(val body: String, val validators: HttpValidators?)

    /**
     * Library names matching [query] (for completion), or `null` on failure. cdnjs uses its own
     * search; unpkg and jsDelivr use the npm search. Network here; call off the EDT.
     */
    fun search(provider: String?, query: String, limit: Int = SEARCH_LIMIT): List<LibrarySuggestion>? {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        return when (normalize(provider)) {
            "filesystem" -> emptyList()
            "unpkg", "jsdelivr" -> get(npmSearchUrl(q, limit))?.let(CatalogParsers::parseNpmSearch)
            else -> get(cdnjsSearchUrl(q, limit))?.let(CatalogParsers::parseCdnjsSearch)
        }
    }

    /**
     * The files of [name]@[version], as `libman.json` lists them (no leading `/`), or `null` on
     * failure. unpkg serves the npm package, so it uses the same jsDelivr file list. Network here.
     */
    fun files(provider: String?, name: String, version: String): List<String>? = when (normalize(provider)) {
        "filesystem" -> null
        "unpkg" -> get(jsdelivrFilesUrl("npm", name, version))?.let(CatalogParsers::parseJsdelivrFiles)
        "jsdelivr" -> get(jsdelivrFilesUrl(if (isGitHubForm(name)) "gh" else "npm", name, version))
            ?.let(CatalogParsers::parseJsdelivrFiles)
        else -> get(cdnjsFilesUrl(name, version))?.let(CatalogParsers::parseCdnjsFiles)
    }

    /** The provider's human-facing page for the library, or `null` when there isn't one. */
    fun pageUrl(provider: String?, name: String): String? = when (normalize(provider)) {
        "filesystem" -> null
        "unpkg" -> "https://www.npmjs.com/package/$name"
        "jsdelivr" -> if (isGitHubForm(name)) "https://www.jsdelivr.com/package/gh/$name"
        else "https://www.jsdelivr.com/package/npm/$name"
        else -> "https://cdnjs.com/libraries/$name"
    }

    /** GET [url] as a string, or `null` on any failure (cancellation still propagates). */
    private fun get(url: String): String? = try {
        HttpRequests.request(url)
            .accept("application/json")
            .connectTimeout(TIMEOUT_MS)
            .readTimeout(TIMEOUT_MS)
            .readString(ProgressManager.getInstance().progressIndicator)
    } catch (e: ProcessCanceledException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private fun normalize(provider: String?): String =
        provider?.trim()?.lowercase()?.ifEmpty { null } ?: "cdnjs"

    /** A jsDelivr GitHub reference is `owner/repo` - has a slash and is not a scoped npm name. */
    private fun isGitHubForm(name: String): Boolean = !name.startsWith("@") && name.contains('/')

    private fun cdnjsUrl(name: String) =
        "https://api.cdnjs.com/libraries/${enc(name)}?fields=versions,description"

    private fun npmUrl(name: String) = "https://registry.npmjs.org/${encScoped(name)}"

    private fun jsdelivrGhUrl(name: String) = "https://data.jsdelivr.com/v1/packages/gh/$name"

    private fun cdnjsSearchUrl(query: String, limit: Int) =
        "https://api.cdnjs.com/libraries?search=${enc(query)}&fields=version,description&limit=$limit"

    private fun npmSearchUrl(query: String, limit: Int) =
        "https://registry.npmjs.org/-/v1/search?text=${enc(query)}&size=$limit"

    private fun cdnjsFilesUrl(name: String, version: String) =
        "https://api.cdnjs.com/libraries/${enc(name)}/${enc(version)}?fields=files"

    // [type] is "npm" or "gh". jsDelivr takes a scoped npm name and an owner/repo as they are.
    private fun jsdelivrFilesUrl(type: String, name: String, version: String) =
        "https://data.jsdelivr.com/v1/packages/$type/$name@${enc(version)}?structure=flat"

    private fun enc(s: String): String = URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20")

    /** npm accepts a scoped name with the slash percent-encoded: `@scope%2Fname`. */
    private fun encScoped(name: String): String =
        if (name.startsWith("@")) name.replace("/", "%2F") else enc(name)
}
