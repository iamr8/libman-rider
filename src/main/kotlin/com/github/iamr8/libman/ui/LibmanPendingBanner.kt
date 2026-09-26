package com.github.iamr8.libman.ui

import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import java.util.function.Function
import javax.swing.JComponent

/**
 * A banner on top of `libman.json` while it has queued changes: how many, from which manifest,
 * with "Apply now" and "Discard". [LibmanPendingService] refreshes it on every queue change.
 */
class LibmanPendingBanner : EditorNotificationProvider, DumbAware {

    override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent?>? {
        if (!file.name.equals(ManifestPsi.FILE_NAME, ignoreCase = true)) return null
        val dir = file.parent?.path ?: return null
        val service = project.serviceIfCreated<LibmanPendingService>() ?: return null
        val count = service.count(dir)
        if (count == 0) return null
        val from = project.basePath?.let { FileUtil.getRelativePath(it, file.path, '/') } ?: file.path
        return Function { editor ->
            EditorNotificationPanel(editor, EditorNotificationPanel.Status.Info).apply {
                text("LibMan: $count pending ${if (count == 1) "change" else "changes"} - from $from")
                createActionLabel("Apply now") { service.apply(dir) }
                createActionLabel("Discard") { service.discard(dir) }
            }
        }
    }
}
