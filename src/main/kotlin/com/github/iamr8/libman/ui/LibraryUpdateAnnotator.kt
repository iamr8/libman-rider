package com.github.iamr8.libman.ui

import com.github.iamr8.libman.model.UpdateBuckets
import com.github.iamr8.libman.provider.LibmanCatalogService
import com.github.iamr8.libman.provider.ProviderCatalog
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.ui.JBColor
import java.awt.Color

/**
 * Highlights the version inside each `library` value of a `libman.json` with a single amber
 * background when any update is available (no per-severity colors). Runs the network fetch in
 * [doAnnotate], which the platform calls off the highlighting thread; results are cached in
 * [LibmanCatalogService].
 */
class LibraryUpdateAnnotator :
    DumbAware,
    ExternalAnnotator<LibraryUpdateAnnotator.Collected, List<LibraryUpdateAnnotator.Result>>() {

    data class Entry(
        val provider: String?,
        val name: String,
        val version: String,
        val versionRange: TextRange,
    )

    data class Collected(val project: Project, val entries: List<Entry>)

    data class Result(val range: TextRange, val tooltip: String)

    override fun collectInformation(file: PsiFile): Collected? {
        if (!ManifestPsi.isManifest(file)) return null
        // collectInformation is called by the platform inside a read action.
        val entries = ManifestPsi.libraryObjects(file).mapNotNull { obj ->
            val ctx = ManifestPsi.contextOf(obj, file) ?: return@mapNotNull null
            val version = ctx.id.version ?: return@mapNotNull null
            if (!ProviderCatalog.isSupported(ctx.provider)) return@mapNotNull null
            val range = ManifestPsi.versionRange(obj, ctx) ?: return@mapNotNull null
            Entry(ctx.provider, ctx.id.name, version, range)
        }
        return if (entries.isEmpty()) null else Collected(file.project, entries)
    }

    override fun doAnnotate(collectedInfo: Collected): List<Result> {
        val service = LibmanCatalogService.getInstance(collectedInfo.project)
        var fetchedAny = false
        val results = collectedInfo.entries.mapNotNull { e ->
            // A cache miss means this pass performs a network fetch; note it so we can trigger a
            // single re-render at the end (which lets the inlay pass pick up the now-filled cache).
            if (service.getCached(e.provider, e.name) == null) fetchedAny = true
            val info = service.getOrFetch(e.provider, e.name) ?: return@mapNotNull null
            val buckets = UpdateBuckets.compute(e.version, info.versions, includePrerelease = true)
            if (!buckets.hasAny()) return@mapNotNull null
            val tip = buckets.candidates().joinToString(", ") { "${it.version.raw} (${it.kind.label})" }
            Result(e.versionRange, "LibMan: update available - $tip")
        }
        if (fetchedAny) service.requestRefresh()
        return results
    }

    override fun apply(file: PsiFile, annotationResult: List<Result>, holder: AnnotationHolder) {
        for (r in annotationResult) {
            // Silent: a pure background color with a hover tooltip, not a Problems-view entry.
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(r.range)
                .enforcedTextAttributes(TextAttributes().apply { backgroundColor = UPDATE_BG })
                .tooltip(r.tooltip)
                .create()
        }
    }

    companion object {
        // A single amber/tan highlight for "an update is available" (light / dark).
        private val UPDATE_BG: Color = JBColor(Color(0xFF, 0xF3, 0xD6), Color(0x5C, 0x52, 0x38))
    }
}
