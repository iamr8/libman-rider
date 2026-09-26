package com.github.iamr8.libman.ui

import com.github.iamr8.libman.model.DestinationDirs
import com.github.iamr8.libman.model.LibraryId
import com.github.iamr8.libman.model.LibraryInput
import com.github.iamr8.libman.model.ManifestFiles
import com.github.iamr8.libman.model.VersionSuggestions
import com.github.iamr8.libman.provider.LibmanCatalogService
import com.github.iamr8.libman.provider.LibraryVersionRef
import com.github.iamr8.libman.settings.LibmanSettings
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.PlainPrefixMatcher
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.json.psi.JsonArray
import com.intellij.json.psi.JsonFile
import com.intellij.json.psi.JsonObject
import com.intellij.json.psi.JsonProperty
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.util.concurrency.AppExecutorUtil
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Completion inside `libman.json` values, like Visual Studio's LibMan:
 *  - `library`: library names from the provider's search; after the version `@`, its newest versions.
 *  - `destination` / `defaultDestination`: folders under the manifest's folder.
 *  - `files`: the files of the entry's library version (the ones not listed yet).
 *
 * Provider data comes from [LibmanCatalogService] (cache first). A network call runs on a pooled
 * thread; this thread only waits and checks for cancel, so typing (a write action) or closing the
 * popup is never blocked by the network.
 */
class LibmanCompletionContributor : CompletionContributor(), DumbAware {

    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        val original = parameters.originalFile
        if (!ManifestPsi.isManifest(original)) return
        val literal = parameters.position.parent as? JsonStringLiteral ?: return
        val start = literal.textRange.startOffset + 1 // after the opening quote
        if (parameters.offset < start) return
        val typed = literal.text.substring(1, parameters.offset - literal.textRange.startOffset)
        val service = LibmanCatalogService.getInstance(original.project)

        val property = literal.parent as? JsonProperty
        val entry = (property?.parent ?: literal.parent?.parent?.parent) as? JsonObject
        when {
            property != null && property.value == literal && property.name == "library" &&
                entry != null && ManifestPsi.isLibraryEntry(entry) ->
                completeLibrary(entry, original, typed, service, result)

            property != null && property.value == literal && (
                (property.name == "destination" && entry != null && ManifestPsi.isLibraryEntry(entry)) ||
                    // The completion copy's root (entry is from the copy, not the original file).
                    (property.name == "defaultDestination" && entry == (literal.containingFile as? JsonFile)?.topLevelValue)
                ) ->
                original.virtualFile?.parent?.let { completeFolders(it, typed, result) }

            literal.parent is JsonArray && (literal.parent.parent as? JsonProperty)?.name == "files" &&
                entry != null && ManifestPsi.isLibraryEntry(entry) ->
                completeFiles(entry, literal.parent as JsonArray, literal, original, typed, service, result)
        }
    }

    private fun completeLibrary(
        entry: JsonObject,
        original: PsiFile,
        typed: String,
        service: LibmanCatalogService,
        result: CompletionResultSet,
    ) {
        val provider = ManifestPsi.effectiveProvider(entry, original)
        if (provider?.trim()?.lowercase() == LibraryId.FILESYSTEM_PROVIDER) return
        when (val input = LibraryInput.parse(typed)) {
            is LibraryInput.Name -> {
                if (input.prefix.isBlank()) return
                val rs = result.withPrefixMatcher(input.prefix)
                // The provider filters by the query, so ask again for every new prefix.
                rs.restartCompletionOnAnyPrefixChange()
                val hits = awaitCancellable { service.search(provider, input.prefix) } ?: return
                hits.forEachIndexed { i, hit ->
                    val element = LookupElementBuilder.create(hit.name)
                        .withTypeText(hit.version, true)
                        .withTailText(hit.description?.let { "  " + it.take(DESCRIPTION_CHARS) }, true)
                    rs.addElement(PrioritizedLookupElement.withPriority(element, (hits.size - i).toDouble()))
                }
            }
            is LibraryInput.Version -> {
                val rs = result.withPrefixMatcher(PlainPrefixMatcher(input.prefix, true))
                val info = service.getCached(provider, input.name)
                    ?: awaitCancellable { service.getOrFetch(provider, input.name) }
                    ?: return
                val versions = VersionSuggestions.latest(info.versions, LibmanSettings.getInstance().includePrereleases)
                versions.forEachIndexed { i, v ->
                    rs.addElement(PrioritizedLookupElement.withPriority(LookupElementBuilder.create(v), (versions.size - i).toDouble()))
                }
            }
        }
    }

    private fun completeFolders(base: VirtualFile, typed: String, result: CompletionResultSet) {
        val rs = result.withPrefixMatcher(PlainPrefixMatcher(typed))
        val found = mutableListOf<String>()
        fun walk(dir: VirtualFile, path: String, depth: Int) {
            for (child in dir.children) {
                if (found.size >= MAX_FOLDERS) return
                if (!child.isDirectory || DestinationDirs.skip(child.name)) continue
                val rel = "$path${child.name}/"
                found += rel
                if (depth < MAX_FOLDER_DEPTH) walk(child, rel, depth + 1)
            }
        }
        walk(base, "", 1)
        found.forEach { rs.addElement(LookupElementBuilder.create(it)) }
    }

    private fun completeFiles(
        entry: JsonObject,
        array: JsonArray,
        literal: JsonStringLiteral,
        original: PsiFile,
        typed: String,
        service: LibmanCatalogService,
        result: CompletionResultSet,
    ) {
        val ctx = ManifestPsi.contextOf(entry, original) ?: return
        val version = ctx.id.version ?: return
        if (ctx.isFilesystem) return
        val ref = LibraryVersionRef(ctx.provider, ctx.id.name, version)
        val files = service.getCachedFiles(ref)
            ?: awaitCancellable { service.getOrFetchFiles(ref, retryFailed = true) }
            ?: return
        val listed = array.valueList.filterIsInstance<JsonStringLiteral>()
            .filter { it != literal }
            .mapTo(HashSet()) { ManifestFiles.normalize(it.value) }
        val rs = result.withPrefixMatcher(PlainPrefixMatcher(typed))
        files.asSequence()
            .filter { it !in listed }
            .take(MAX_FILES)
            .forEach { rs.addElement(LookupElementBuilder.create(it)) }
    }

    /** Runs [task] on a pooled thread and waits, checking for cancel (a keystroke, a closed popup). */
    private fun <T> awaitCancellable(task: () -> T): T {
        val future = AppExecutorUtil.getAppExecutorService().submit(Callable { task() })
        while (true) {
            ProgressManager.checkCanceled()
            try {
                return future.get(POLL_MS, TimeUnit.MILLISECONDS)
            } catch (e: TimeoutException) {
                // still running: check for cancel again
            } catch (e: ExecutionException) {
                throw e.cause ?: e
            }
        }
    }

    private companion object {
        const val POLL_MS = 20L
        const val DESCRIPTION_CHARS = 60
        const val MAX_FOLDERS = 300
        const val MAX_FOLDER_DEPTH = 4
        const val MAX_FILES = 1000
    }
}
