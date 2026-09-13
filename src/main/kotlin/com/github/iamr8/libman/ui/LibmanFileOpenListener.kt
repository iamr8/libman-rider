package com.github.iamr8.libman.ui

import com.github.iamr8.libman.provider.LibmanCatalogService
import com.github.iamr8.libman.provider.ProviderCatalog
import com.github.iamr8.libman.settings.LibmanSettings
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager

/**
 * Force-checks every library for updates when a `libman.json` is opened, and cancels that check when
 * the file is closed. The service coalesces a close+reopen so the sweep does not run twice.
 */
class LibmanFileOpenListener : FileEditorManagerListener {

    override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        if (!file.name.equals(ManifestPsi.FILE_NAME, ignoreCase = true)) return
        if (!LibmanSettings.getInstance().checkOnOpen) return
        val project = source.project
        LibmanCatalogService.getInstance(project).sweepOnOpen(file, readEntries(project, file))
    }

    override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
        if (!file.name.equals(ManifestPsi.FILE_NAME, ignoreCase = true)) return
        LibmanCatalogService.getInstance(source.project).cancelSweep(file)
    }

    private fun readEntries(project: Project, file: VirtualFile): List<Pair<String?, String>> =
        ReadAction.compute<List<Pair<String?, String>>, RuntimeException> {
            if (project.isDisposed) return@compute emptyList()
            val psi = PsiManager.getInstance(project).findFile(file) ?: return@compute emptyList()
            ManifestPsi.libraryObjects(psi).mapNotNull { obj ->
                val ctx = ManifestPsi.contextOf(obj, psi) ?: return@mapNotNull null
                if (!ProviderCatalog.isSupported(ctx.provider)) return@mapNotNull null
                ctx.provider to ctx.id.name
            }
        }
}
