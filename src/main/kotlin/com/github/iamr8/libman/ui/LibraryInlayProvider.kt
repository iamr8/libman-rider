@file:Suppress("UnstableApiUsage")

package com.github.iamr8.libman.ui

import com.github.iamr8.libman.model.TextWrap
import com.github.iamr8.libman.model.UpdateBuckets
import com.github.iamr8.libman.model.UpdateLabel
import com.github.iamr8.libman.provider.LibmanCatalogService
import com.github.iamr8.libman.provider.ProviderCatalog
import com.intellij.codeInsight.hints.ChangeListener
import com.intellij.codeInsight.hints.FactoryInlayHintsCollector
import com.intellij.codeInsight.hints.ImmediateConfigurable
import com.intellij.codeInsight.hints.InlayPresentationFactory.Padding
import com.intellij.codeInsight.hints.InlayHintsCollector
import com.intellij.codeInsight.hints.InlayHintsProvider
import com.intellij.codeInsight.hints.InlayHintsSink
import com.intellij.codeInsight.hints.NoSettings
import com.intellij.codeInsight.hints.SettingsKey
import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.codeInsight.hints.presentation.MouseButton
import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.json.psi.JsonObject
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import java.awt.Cursor
import javax.swing.JPanel

/**
 * Renders a block above each `library` line in `libman.json`, indented to the `library` column:
 * the provider's description (up to 3 lines, truncated) and a row of clickable chips - "Check for
 * updates" plus one "Update to X" per available version. All data comes from [LibmanCatalogService]'s
 * cache (no network here); [LibraryUpdateAnnotator] fills it.
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

                // Indent every block line to the column of the `library` property, so the inlays line
                // up with the object's own properties instead of the editor's left edge.
                val doc = editor.document
                val col = offset - doc.getLineStartOffset(doc.getLineNumber(offset))

                val info = service.getCached(ctx.provider, ctx.id.name)
                val buckets = ctx.id.version?.let {
                    info?.let { i -> UpdateBuckets.compute(it, i.versions, includePrerelease = true) }
                }

                // Block-above elements render higher priority closest to the anchor line, so we
                // increment: the description lines are added first (lowest priority) and sit on top;
                // the single action row is added last (highest) and sits just above the library line.
                var priority = 100
                fun addLine(content: InlayPresentation) {
                    // Prepend an indent in the editor font (spaces == columns) so it aligns with code.
                    val line = factory.join(listOf(factory.text(" ".repeat(col)), content)) { factory.text("") }
                    sink.addBlockElement(offset, relatesToPrecedingText = true, showAbove = true, priority = priority++, line)
                }

                // Description on top: up to 3 gray lines, comment-styled with a left bar. Vertical
                // padding gives the lines breathing room.
                TextWrap.wrap(info?.description, maxWidth = 88, maxLines = 3).forEach {
                    addLine(factory.container(factory.smallText("│ $it"), padding = Padding(0, 0, 3, 3)))
                }

                // One action row just above the line: "Check for updates" + one "Update to X" per version.
                val actions = mutableListOf<InlayPresentation>()
                actions += chip("↻ Check for updates") { service.refreshInBackground(ctx.provider, ctx.id.name) }
                val candidates = buckets?.candidates().orEmpty()
                candidates.forEach { c ->
                    val label = UpdateLabel.chip(c.version.raw, c.kind.label, single = candidates.size == 1)
                    actions += chip(label) {
                        LibmanOps.update(project, ctx.manifestDir, ctx.id.name, to = c.version.raw)
                    }
                }
                addLine(factory.join(actions) { factory.smallText("  ") })
                return true
            }

            // A clickable chip: keep the rounded-background styling, but show a hand cursor on hover.
            private fun chip(text: String, onClick: () -> Unit): InlayPresentation =
                factory.withCursorOnHover(
                    factory.onClick(factory.roundWithBackground(factory.smallText(text)), MouseButton.Left) { _, _ -> onClick() },
                    Cursor.getPredefinedCursor(Cursor.HAND_CURSOR),
                )
        }
    }
}
