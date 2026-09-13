package com.github.iamr8.libman.ui

import com.github.iamr8.libman.cli.LibmanLocator
import com.github.iamr8.libman.cli.LibmanRunner
import com.github.iamr8.libman.provider.LibmanCatalogService
import com.github.iamr8.libman.provider.ProviderCatalog
import com.github.iamr8.libman.settings.LibmanSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import java.util.concurrent.atomic.AtomicBoolean

/**
 * On opening a `libman.json`: warns once (per project) if the libman CLI is missing, and - when
 * check-on-open is enabled - force-checks every library for updates, cancelling that check when the
 * file is closed. The service coalesces a close+reopen so the sweep does not run twice.
 */
class LibmanFileOpenListener : FileEditorManagerListener {

    // One instance per project (projectListeners) -> the install check runs once per project session.
    private val installVerified = AtomicBoolean(false)

    override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        if (!file.name.equals(ManifestPsi.FILE_NAME, ignoreCase = true)) return
        val project = source.project
        // The install warning is independent of check-on-open: a user who turned off update checks
        // still needs to know the CLI is missing before an action fails.
        verifyLibmanInstalled(project)
        if (!LibmanSettings.getInstance().checkOnOpen) return
        LibmanCatalogService.getInstance(project).sweepOnOpen(file, readEntries(project, file))
    }

    override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
        if (!file.name.equals(ManifestPsi.FILE_NAME, ignoreCase = true)) return
        LibmanCatalogService.getInstance(source.project).cancelSweep(file)
    }

    /**
     * Warn once per project if libman is not installed. Fast path: if the locator finds an existing
     * executable (custom path, PATH, or `~/.dotnet/tools`) no process is spawned. Only the
     * bare-command fallback needs a short `--version` spawn to confirm.
     */
    private fun verifyLibmanInstalled(project: Project) {
        if (!installVerified.compareAndSet(false, true)) return
        val customPath = LibmanSettings.getInstance().customLibmanPath
        ApplicationManager.getApplication().executeOnPooledThread {
            if (LibmanLocator.resolveExisting(customPath) != null) return@executeOnPooledThread
            val ok = LibmanRunner(LibmanLocator.resolve(customPath)).isInstalled(timeoutMs = 5_000)
            if (!ok && !project.isDisposed) {
                ApplicationManager.getApplication().invokeLater(
                    { if (!project.isDisposed) LibmanNotifications.notInstalled(project) },
                    ModalityState.any(),
                )
            }
        }
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
