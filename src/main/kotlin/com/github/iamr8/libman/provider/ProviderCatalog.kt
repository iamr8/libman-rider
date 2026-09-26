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

    /** The provider's human-facing page for the library, or `null` when there isn't one. */
    fun pageUrl(provider: String?, name: String): String? = when (normalize(provider)) {
        "filesystem" -> null
        "unpkg" -> "https://www.npmjs.com/package/$name"
        "jsdelivr" -> if (isGitHubForm(name)) "https://www.jsdelivr.com/package/gh/$name"
        else "https://www.jsdelivr.com/package/npm/$name"
        else -> "https://cdnjs.com/libraries/$name"
    }

    private fun normalize(provider: String?): String =
        provider?.trim()?.lowercase()?.ifEmpty { null } ?: "cdnjs"

    /** A jsDelivr GitHub reference is `owner/repo` - has a slash and is not a scoped npm name. */
    private fun isGitHubForm(name: String): Boolean = !name.startsWith("@") && name.contains('/')

    private fun cdnjsUrl(name: String) =
        "https://api.cdnjs.com/libraries/${enc(name)}?fields=versions,description"

    private fun npmUrl(name: String) = "https://registry.npmjs.org/${encScoped(name)}"

    private fun jsdelivrGhUrl(name: String) = "https://data.jsdelivr.com/v1/packages/gh/$name"

    private fun enc(s: String): String = URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20")

    /** npm accepts a scoped name with the slash percent-encoded: `@scope%2Fname`. */
    private fun encScoped(name: String): String =
        if (name.startsWith("@")) name.replace("/", "%2F") else enc(name)
}
