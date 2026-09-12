package com.github.iamr8.libman.ui

import com.github.iamr8.libman.model.SeverityColor
import com.github.iamr8.libman.model.UpdateBuckets
import com.github.iamr8.libman.provider.LibmanCatalogService
import com.github.iamr8.libman.provider.ProviderCatalog
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.ui.JBColor
import java.awt.Color

/**
 * Colors the version inside each `library` value of a `libman.json` by the biggest available update
 * (green=patch, yellow=minor, red=major/prerelease). Runs the network fetch in [doAnnotate], which
 * the platform calls off the highlighting thread; results are cached in [LibmanCatalogService].
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

    data class Result(val range: TextRange, val color: SeverityColor, val tooltip: String)

    override fun collectInformation(file: PsiFile): Collected? {
        if (!ManifestPsi.isManifest(file)) return null
        val entries = runReadAction {
            ManifestPsi.libraryObjects(file).mapNotNull { obj ->
                val ctx = ManifestPsi.contextOf(obj, file) ?: return@mapNotNull null
                val version = ctx.id.version ?: return@mapNotNull null
                if (!ProviderCatalog.isSupported(ctx.provider)) return@mapNotNull null
                val range = ManifestPsi.versionRange(obj, ctx) ?: return@mapNotNull null
                Entry(ctx.provider, ctx.id.name, version, range)
            }
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
            Result(e.versionRange, buckets.highestColor(), "LibMan: update available - $tip")
        }
        if (fetchedAny) service.requestRefresh()
        return results
    }

    override fun apply(file: PsiFile, annotationResult: List<Result>, holder: AnnotationHolder) {
        for (r in annotationResult) {
            val bg = backgroundFor(r.color) ?: continue
            // Silent: a pure background color with a hover tooltip, not a Problems-view entry.
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(r.range)
                .enforcedTextAttributes(TextAttributes().apply { backgroundColor = bg })
                .tooltip(r.tooltip)
                .create()
        }
    }

    private fun backgroundFor(color: SeverityColor): Color? = when (color) {
        SeverityColor.RED -> JBColor(Color(0xFF, 0xE3, 0xE3), Color(0x5C, 0x3A, 0x3A))
        SeverityColor.YELLOW -> JBColor(Color(0xFF, 0xF3, 0xD6), Color(0x5C, 0x52, 0x38))
        SeverityColor.GREEN -> JBColor(Color(0xE5, 0xF5, 0xE6), Color(0x36, 0x5C, 0x3C))
        SeverityColor.NONE -> null
    }
}
