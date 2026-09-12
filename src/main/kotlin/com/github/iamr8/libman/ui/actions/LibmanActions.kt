package com.github.iamr8.libman.ui.actions

import com.github.iamr8.libman.ui.LibmanOps
import com.github.iamr8.libman.ui.ManifestPsi
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.vfs.VirtualFile

/** True when the context's target file is a `libman.json`. */
private fun libmanFile(e: AnActionEvent): VirtualFile? =
    e.getData(CommonDataKeys.VIRTUAL_FILE)?.takeIf { it.name.equals(ManifestPsi.FILE_NAME, ignoreCase = true) }

/**
 * The "Client-Side Libraries (LibMan)" context sub-menu. It is shown only when a `libman.json`
 * is the selected/open file, so it never clutters other files' menus.
 *
 * Added to `ProjectViewPopupMenu` (Solution Explorer) and `EditorPopupMenu` in plugin.xml.
 */
class LibmanActionGroup : DefaultActionGroup() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = libmanFile(e) != null
    }
}

/** Base for the context-menu actions: resolves the manifest folder and self-gates on `libman.json`. */
abstract class LibmanFileAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = libmanFile(e) != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = libmanFile(e) ?: return
        val manifestDir = file.parent?.path ?: return
        // libman reads libman.json from disk; flush any unsaved edits first.
        FileDocumentManager.getInstance().saveAllDocuments()
        perform(e, project, file, manifestDir)
    }

    protected abstract fun perform(
        e: AnActionEvent,
        project: com.intellij.openapi.project.Project,
        file: VirtualFile,
        manifestDir: String,
    )
}

/** Restore every library defined in the manifest. */
class RestoreAction : LibmanFileAction() {
    override fun perform(e: AnActionEvent, project: com.intellij.openapi.project.Project, file: VirtualFile, manifestDir: String) =
        LibmanOps.restore(project, manifestDir)
}

/** Delete files previously restored via LibMan (manifest entries stay). */
class CleanAction : LibmanFileAction() {
    override fun perform(e: AnActionEvent, project: com.intellij.openapi.project.Project, file: VirtualFile, manifestDir: String) =
        LibmanOps.clean(project, manifestDir)
}

/** Open the manifest for editing. */
class ManageAction : LibmanFileAction() {
    override fun perform(e: AnActionEvent, project: com.intellij.openapi.project.Project, file: VirtualFile, manifestDir: String) {
        FileEditorManager.getInstance(project).openFile(file, true)
    }
}
