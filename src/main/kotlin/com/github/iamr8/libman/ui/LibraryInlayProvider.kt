@file:Suppress("UnstableApiUsage")

package com.github.iamr8.libman.ui

import com.github.iamr8.libman.model.PendingChange
import com.github.iamr8.libman.model.PendingChanges
import com.github.iamr8.libman.model.UpdateBuckets
import com.github.iamr8.libman.model.UpdateLabel
import com.github.iamr8.libman.provider.LibmanCatalogService
import com.github.iamr8.libman.provider.ProviderCatalog
import com.github.iamr8.libman.settings.LibmanSettings
import com.intellij.codeInsight.hints.ChangeListener
import com.intellij.codeInsight.hints.FactoryInlayHintsCollector
import com.intellij.codeInsight.hints.ImmediateConfigurable
import com.intellij.codeInsight.hints.InlayHintsCollector
import com.intellij.codeInsight.hints.InlayHintsProvider
import com.intellij.codeInsight.hints.InlayHintsSink
import com.intellij.codeInsight.hints.NoSettings
import com.intellij.codeInsight.hints.SettingsKey
import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.ui.Messages
import com.intellij.json.psi.JsonObject
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import javax.swing.Icon
import javax.swing.JPanel

/**
 * Renders one clickable action row above each `library` line in `libman.json`, indented to the
 * `library` column: "Check for updates", one "Update to X" per available version, and "Remove".
 * Update and Remove queue the change ([LibmanPendingService]); a queued library shows
 * "Pending: ..." and "Undo" instead.
 * The links have no background; the description is shown as a hover tooltip by [LibraryUpdateAnnotator],
 * which also fills [LibmanCatalogService]'s cache (no network here).
 */
class LibraryInlayProvider : InlayHintsProvider<NoSettings> {

    override val key: SettingsKey<NoSettings> = SettingsKey("libman.libraries.block")
    override val name: String = "LibMan libraries"
    override val previewText: String? = null

    override fun createSettings(): NoSettings = NoSettings()

    override fun createConfigurable(settings: NoSettings): ImmediateConfigurable =
        object : ImmediateConfigurable {
            override fun createComponent(listener: ChangeListener) = JPanel()
        }

    override fun getCollectorFor(
        file: PsiFile,
        editor: Editor,
        settings: NoSettings,
        sink: InlayHintsSink,
    ): InlayHintsCollector? {
        if (!ManifestPsi.isManifest(file)) return null
        val project = file.project
        val service = LibmanCatalogService.getInstance(project)
        val pending = LibmanPendingService.getInstance(project)

        return object : FactoryInlayHintsCollector(editor) {
            override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
                if (element !is JsonObject || !ManifestPsi.isLibraryEntry(element)) return true
                val ctx = ManifestPsi.contextOf(element, file) ?: return true
                if (!ProviderCatalog.isSupported(ctx.provider)) return true
                val libraryProp = element.findProperty("library") ?: return true
                val offset = libraryProp.textRange.startOffset

                // Indent the row to the `library` property's column (pixel-exact, so it tracks the
                // prop if it moves): column count times the editor's plain space width.
                val doc = editor.document
                val col = offset - doc.getLineStartOffset(doc.getLineNumber(offset))
                val leftPad = EditorUtil.getPlainSpaceWidth(editor) * col

                val info = service.getCached(ctx.provider, ctx.id.name)
                val buckets = ctx.id.version?.let { v ->
                    info?.let { UpdateBuckets.compute(v, it.versions, LibmanSettings.getInstance().includePrereleases) }
                }

                val links = mutableListOf<InlayPresentation>()
                val queued = pending.pending(ctx.manifestDir, ctx.id.name)
                if (queued != null) {
                    // A queued change replaces the row until it runs (save / close) or is undone.
                    links += factory.seq(
                        factory.smallScaledIcon(AllIcons.General.Information),
                        factory.smallText(" Pending: ${PendingChanges.label(queued)}"),
                    )
                    links += link(AllIcons.Actions.Undo, "Undo") { pending.cancel(ctx.manifestDir, ctx.id.name) }
                } else {
                    val recheck = { service.refreshInBackground(ctx.provider, ctx.id.name) }
                    // A failed lookup must not look like "up to date": show it, with the reason on hover.
                    val failure = service.getFailure(ctx.provider, ctx.id.name)
                    links += if (failure == null) {
                        link(AllIcons.Actions.Refresh, "Check for updates", recheck)
                    } else {
                        factory.withTooltip(
                            "Could not check for updates: $failure",
                            link(AllIcons.General.Warning, "Check failed. Retry", recheck),
                        )
                    }
                    val candidates = buckets?.candidates().orEmpty()
                    candidates.forEach { c ->
                        val label = UpdateLabel.chip(c.version.raw, c.kind.label, single = candidates.size == 1)
                        links += link(AllIcons.Actions.Download, label) {
                            pending.enqueue(ctx.manifestDir, PendingChange.Update(ctx.id.name, c.version.raw))
                        }
                    }
                    // AllIcons.Actions.GC is the trash-bin glyph (expui/general/delete.svg).
                    links += link(AllIcons.Actions.GC, "Remove") {
                        // Remove deletes the library's files; confirm before the destructive step.
                        val confirmed = Messages.showYesNoDialog(
                            project,
                            "Remove \"${ctx.id.name}\"? Its files are deleted when you save or close libman.json.",
                            "Remove Library",
                            Messages.getQuestionIcon(),
                        ) == Messages.YES
                        if (confirmed) pending.enqueue(ctx.manifestDir, PendingChange.Remove(ctx.id.name))
                    }
                }

                val row = factory.inset(factory.join(links) { factory.smallText("   ") }, left = leftPad)
                sink.addBlockElement(offset, relatesToPrecedingText = true, showAbove = true, priority = 100, row)
                return true
            }

            // A background-free clickable link (icon + label): hand cursor + underline on hover.
            // smallScaledIcon scales the icon to the small-text metrics and adds the same top/down
            // inset as smallText, so the icon and label share one baseline (plain icon() renders at
            // full size and sits lower than the small text).
            private fun link(icon: Icon, text: String, onClick: () -> Unit): InlayPresentation =
                factory.referenceOnHover(
                    factory.seq(factory.smallScaledIcon(icon), factory.smallText(" $text")),
                ) { _, _ -> onClick() }
        }
    }
}
