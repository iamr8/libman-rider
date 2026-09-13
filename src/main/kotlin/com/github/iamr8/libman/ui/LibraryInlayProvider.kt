@file:Suppress("UnstableApiUsage")

package com.github.iamr8.libman.ui

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
                links += link(AllIcons.Actions.Refresh, "Check for updates") { service.refreshInBackground(ctx.provider, ctx.id.name) }
                val candidates = buckets?.candidates().orEmpty()
                candidates.forEach { c ->
                    val label = UpdateLabel.chip(c.version.raw, c.kind.label, single = candidates.size == 1)
                    links += link(AllIcons.Actions.Download, label) { LibmanOps.update(project, ctx.manifestDir, ctx.id.name, to = c.version.raw) }
                }
                links += link(AllIcons.General.Remove, "Remove") {
                    // Remove deletes the library's files; confirm before the destructive step.
                    val confirmed = Messages.showYesNoDialog(
                        project,
                        "Remove \"${ctx.id.name}\" and delete its files?",
                        "Remove Library",
                        Messages.getQuestionIcon(),
                    ) == Messages.YES
                    if (confirmed) LibmanOps.uninstall(project, ctx.manifestDir, ctx.id.name)
                }

                val row = factory.inset(factory.join(links) { factory.smallText("   ") }, left = leftPad)
                sink.addBlockElement(offset, relatesToPrecedingText = true, showAbove = true, priority = 100, row)
                return true
            }

            // A background-free clickable link (icon + label): hand cursor + underline on hover.
            private fun link(icon: Icon, text: String, onClick: () -> Unit): InlayPresentation =
                factory.referenceOnHover(
                    factory.seq(factory.icon(icon), factory.smallText(" $text")),
                ) { _, _ -> onClick() }
        }
    }
}
