package com.github.iamr8.libman.ui

import com.github.iamr8.libman.model.LibraryId
import com.github.iamr8.libman.model.LibraryInput
import com.github.iamr8.libman.model.LibraryNameSuggestions
import com.github.iamr8.libman.model.ManifestFiles
import com.github.iamr8.libman.model.VersionSuggestions
import com.github.iamr8.libman.provider.LibmanCatalogService
import com.github.iamr8.libman.provider.LibraryVersionRef
import com.github.iamr8.libman.settings.LibmanSettings
import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.completion.PlainPrefixMatcher
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.json.psi.JsonArray
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
 *  - `library`: library names that start with the typed text (from 3 letters), from the provider's
 *    search (a chosen name writes `name@version`); after the version `@`, its newest versions.
 *  - `files` (of an entry or a `fileMappings` item): the files of the library version that are not
 *    listed yet and not on disk in the destination. A mapping's files are relative to its `root`.
 * `destination` / `defaultDestination` folders come from [LibmanReferenceContributor] (the IDE's
 * own path completion).
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
                completeLibrary(entry, original, typed, (parameters.originalPosition?.parent as? JsonStringLiteral)?.value, service, result)

            literal.parent is JsonArray && (literal.parent.parent as? JsonProperty)?.name == "files" && entry != null -> {
                val array = literal.parent as JsonArray
                if (ManifestPsi.isLibraryEntry(entry)) {
                    completeFiles(entry, null, array, literal, original, typed, service, result)
                } else {
                    // A `fileMappings` item (schema 3.0) of a library entry.
                    ManifestPsi.mappingEntry(entry)?.let { completeFiles(it, entry, array, literal, original, typed, service, result) }
                }
            }
        }
    }

    private fun completeLibrary(
        entry: JsonObject,
        original: PsiFile,
        typed: String,
        currentValue: String?,
        service: LibmanCatalogService,
        result: CompletionResultSet,
    ) {
        val provider = ManifestPsi.effectiveProvider(entry, original)
        if (provider?.trim()?.lowercase() == LibraryId.FILESYSTEM_PROVIDER) return
        when (val input = LibraryInput.parse(typed)) {
            is LibraryInput.Name -> {
                val rs = result.withPrefixMatcher(PlainPrefixMatcher(input.prefix, true))
                // The provider filters by the query, so ask again for every new prefix. Set before
                // the length check: after an empty auto-popup, typing on opens it again only then.
                rs.restartCompletionOnAnyPrefixChange()
                if (!LibraryNameSuggestions.canSearch(input.prefix)) return
                val found = awaitCancellable { service.search(provider, input.prefix) } ?: return
                val hits = LibraryNameSuggestions.startingWith(found, input.prefix, LibmanSettings.getInstance().completionNameLimit) { it.name }
                val current = currentValue?.let { LibraryId.parse(it, provider) }
                hits.forEachIndexed { i, hit ->
                    val element = LookupElementBuilder.create(hit.name)
                        .withInsertHandler(LibraryValueInsert(LibraryNameSuggestions.chosenValue(hit.name, hit.version, current)))
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

    private fun completeFiles(
        entry: JsonObject,
        mapping: JsonObject?,
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
        val offered = if (mapping == null) files else ManifestFiles.underRoot(files, ManifestPsi.mappingRoot(mapping))
        val listed = array.valueList.filterIsInstance<JsonStringLiteral>()
            .filter { it != literal }
            .mapTo(HashSet()) { ManifestFiles.normalize(it.value) }
        // Files already installed in the destination are not offered. A mapping without its own
        // destination uses the library's (schema).
        val destination = mapping?.let { ManifestPsi.mappingDestination(it) }
            ?: ManifestPsi.effectiveDestination(entry, original, ctx.id)
        val installed = destination?.let { dest -> original.virtualFile?.parent?.let { destinationDir(it, dest) } }
        val rs = result.withPrefixMatcher(PlainPrefixMatcher(typed))
        offered.asSequence()
            .filter { it !in listed }
            .filter { installed?.findFileByRelativePath(ManifestFiles.normalize(it)) == null }
            .take(MAX_FILES)
            .forEach { rs.addElement(LookupElementBuilder.create(it)) }
    }

    /** The [destination] folder, relative to the manifest's folder (schema). Null when it does not exist. */
    private fun destinationDir(manifestDir: VirtualFile, destination: String): VirtualFile? =
        ManifestFiles.normalizeDir(destination).takeIf { it.isNotEmpty() }?.let { manifestDir.findFileByRelativePath(it) }

    /**
     * Writes the whole `library` value when a name is chosen, so an old version of another library
     * does not stay. The name prefix starts right after the opening quote.
     */
    private class LibraryValueInsert(private val value: String) : InsertHandler<LookupElement> {
        override fun handleInsert(context: InsertionContext, item: LookupElement) {
            val document = context.document
            val end = LibraryNameSuggestions.closingQuote(document.charsSequence, context.tailOffset) ?: context.tailOffset
            document.replaceString(context.startOffset, end, value)
            context.editor.caretModel.moveToOffset(context.startOffset + value.length)
            // No version known: open the version list after the `@`.
            if (value.endsWith("@")) AutoPopupController.getInstance(context.project).scheduleAutoPopup(context.editor)
        }
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
        const val MAX_FILES = 1000
    }
}
