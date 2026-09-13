package com.github.iamr8.libman.ui

import com.github.iamr8.libman.model.UpdateBuckets
import com.github.iamr8.libman.provider.LibmanCatalogService
import com.github.iamr8.libman.provider.ProviderCatalog
import com.github.iamr8.libman.settings.LibmanSettings
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiFile
import com.intellij.ui.JBColor
import java.awt.Color

/**
 * For each library in a `libman.json`: shows the provider's description as a hover tooltip on the
 * library name (with an "Open on <provider>" link), and highlights the version with a single amber
 * background when any update is available. This renders from the [LibmanCatalogService] cache only -
 * it never fetches here. Every network fetch runs in a visible, cancellable background task (the
 * open-file sweep and the per-library "Check for updates" link), so a cold or expired cache simply
 * shows nothing until the next check.
 */
class LibraryUpdateAnnotator :
    DumbAware,
    ExternalAnnotator<LibraryUpdateAnnotator.Collected, List<LibraryUpdateAnnotator.Result>>() {

    data class Entry(
        val provider: String?,
        val name: String,
        val version: String?,
        val versionRange: TextRange?,
        val nameRange: TextRange,
    )

    data class Collected(val project: Project, val entries: List<Entry>)

    /** A single annotation: a hover tooltip, optionally with the amber "update" background. */
    data class Result(val range: TextRange, val tooltip: String, val highlight: Boolean)

    override fun collectInformation(file: PsiFile): Collected? {
        if (!ManifestPsi.isManifest(file)) return null
        // collectInformation is called by the platform inside a read action.
        val entries = ManifestPsi.libraryObjects(file).mapNotNull { obj ->
            val ctx = ManifestPsi.contextOf(obj, file) ?: return@mapNotNull null
            if (!ProviderCatalog.isSupported(ctx.provider)) return@mapNotNull null
            val nameRange = ManifestPsi.nameRange(obj, ctx) ?: return@mapNotNull null
            Entry(ctx.provider, ctx.id.name, ctx.id.version, ManifestPsi.versionRange(obj, ctx), nameRange)
        }
        return if (entries.isEmpty()) null else Collected(file.project, entries)
    }

    override fun doAnnotate(collectedInfo: Collected): List<Result> {
        val service = LibmanCatalogService.getInstance(collectedInfo.project)
        val includePre = LibmanSettings.getInstance().includePrereleases
        // Cache-only: never fetch on the highlighting thread. Fetching happens in the visible,
        // cancellable open-sweep and "Check for updates" tasks, which call requestRefresh() to
        // re-run this pass once fresh data lands.
        return collectedInfo.entries.flatMap { e ->
            val info = service.getCached(e.provider, e.name) ?: return@flatMap emptyList()
            val out = mutableListOf<Result>()

            // Description tooltip on the library name.
            nameTooltip(info.description, ProviderCatalog.pageUrl(e.provider, e.name), e.provider)?.let {
                out += Result(e.nameRange, it, highlight = false)
            }

            // Amber highlight on the version when an update exists.
            if (e.version != null && e.versionRange != null) {
                val buckets = UpdateBuckets.compute(e.version, info.versions, includePre)
                if (buckets.hasAny()) {
                    val tip = buckets.candidates().joinToString(", ") { "${it.version.raw} (${it.kind.label})" }
                    out += Result(e.versionRange, "LibMan: update available - $tip", highlight = true)
                }
            }
            out
        }
    }

    override fun apply(file: PsiFile, annotationResult: List<Result>, holder: AnnotationHolder) {
        for (r in annotationResult) {
            // Silent: never a Problems-view entry; just a hover tooltip (plus the amber background
            // on version ranges).
            val builder = holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(r.range)
                .tooltip(r.tooltip)
            if (r.highlight) {
                builder.enforcedTextAttributes(TextAttributes().apply { backgroundColor = UPDATE_BG })
            }
            builder.create()
        }
    }

    private fun nameTooltip(description: String?, url: String?, provider: String?): String? {
        val desc = description?.trim().orEmpty()
        if (desc.isEmpty() && url == null) return null
        val sb = StringBuilder("<html>")
        if (desc.isNotEmpty()) sb.append(StringUtil.escapeXmlEntities(desc))
        if (url != null) {
            if (desc.isNotEmpty()) sb.append("<br/><br/>")
            sb.append("<a href=\"").append(url).append("\">Open on ").append(providerLabel(provider)).append(" ↗</a>")
        }
        return sb.append("</html>").toString()
    }

    private fun providerLabel(provider: String?): String = when (provider?.trim()?.lowercase()) {
        "unpkg" -> "npm"
        "jsdelivr" -> "jsDelivr"
        else -> "cdnjs"
    }

    companion object {
        // A single amber/tan highlight for "an update is available" (light / dark).
        private val UPDATE_BG: Color = JBColor(Color(0xFF, 0xF3, 0xD6), Color(0x5C, 0x52, 0x38))
    }
}
