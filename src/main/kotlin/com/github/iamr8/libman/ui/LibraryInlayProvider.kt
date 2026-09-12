@file:Suppress("UnstableApiUsage")

package com.github.iamr8.libman.ui

import com.github.iamr8.libman.model.TextWrap
import com.github.iamr8.libman.model.UpdateBuckets
import com.github.iamr8.libman.provider.LibmanCatalogService
import com.github.iamr8.libman.provider.ProviderCatalog
import com.intellij.codeInsight.hints.ChangeListener
import com.intellij.codeInsight.hints.FactoryInlayHintsCollector
import com.intellij.codeInsight.hints.ImmediateConfigurable
import com.intellij.codeInsight.hints.InlayHintsCollector
import com.intellij.codeInsight.hints.InlayHintsProvider
import com.intellij.codeInsight.hints.InlayHintsSink
import com.intellij.codeInsight.hints.NoSettings
import com.intellij.codeInsight.hints.SettingsKey
import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.codeInsight.hints.presentation.MouseButton
import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.ide.BrowserUtil
import com.intellij.json.psi.JsonObject
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import javax.swing.JPanel

/**
 * Renders a block above each `library` line in `libman.json`: the provider's description
 * (up to 3 lines, truncated), a link to the library's provider page, and a row of clickable chips -
 * "Check for updates" plus one per available version (patch/minor/major/prerelease). All data comes
 * from [LibmanCatalogService]'s cache (no network here); [LibraryUpdateAnnotator] fills it.
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
                val offset = (element.findProperty("library") ?: return true).textRange.startOffset

                val info = service.getCached(ctx.provider, ctx.id.name)
                val buckets = ctx.id.version?.let {
                    info?.let { i -> UpdateBuckets.compute(it, i.versions, includePrerelease = true) }
                }

                var priority = 100
                fun addLine(presentation: InlayPresentation) {
                    sink.addBlockElement(offset, relatesToPrecedingText = true, showAbove = true, priority = priority--, presentation)
                }

                // Description (up to 3 gray lines).
                TextWrap.wrap(info?.description, maxWidth = 90, maxLines = 3).forEach { addLine(factory.smallText(it)) }

                // Provider-page link.
                ProviderCatalog.pageUrl(ctx.provider, ctx.id.name)?.let { url ->
                    addLine(factory.onClick(factory.smallText("Open on ${providerLabel(ctx.provider)}  ↗"), MouseButton.Left) { _, _ ->
                        BrowserUtil.browse(url)
                    })
                }

                // Chips row: "Check for updates" + one per available version.
                val chips = mutableListOf<InlayPresentation>()
                chips += chip("↻ Check for updates") { service.refreshInBackground(ctx.provider, ctx.id.name) }
                buckets?.candidates()?.forEach { c ->
                    chips += chip("${c.version.raw} (${c.kind.label})") {
                        LibmanOps.update(project, ctx.manifestDir, ctx.id.name, to = c.version.raw)
                    }
                }
                addLine(factory.join(chips) { factory.smallText("  ") })
                return true
            }

            private fun chip(text: String, onClick: () -> Unit): InlayPresentation =
                factory.onClick(factory.roundWithBackground(factory.smallText(text)), MouseButton.Left) { _, _ -> onClick() }
        }
    }

    private fun providerLabel(provider: String?): String = when (provider?.trim()?.lowercase()) {
        "unpkg" -> "npm"
        "jsdelivr" -> "jsDelivr"
        else -> "cdnjs"
    }
}
