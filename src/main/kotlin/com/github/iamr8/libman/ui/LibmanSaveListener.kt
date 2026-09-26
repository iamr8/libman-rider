package com.github.iamr8.libman.ui

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionResult
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.project.ProjectCloseListener
import com.intellij.openapi.project.Project

/**
 * Runs the queued LibMan changes on an explicit save. Only the save actions count: auto-save
 * (frame deactivation, idle) saves through FileDocumentManager without these actions, so it never
 * applies a queue. The default keymap binds Ctrl+S to `SaveAll`; the Visual Studio keymaps bind it
 * to `SaveDocument` (Ctrl+Shift+S is `SaveAll` there).
 */
class LibmanSaveListener : AnActionListener {

    override fun afterActionPerformed(action: AnAction, event: AnActionEvent, result: AnActionResult) {
        // Called for every action in the IDE: return early unless a queue is waiting.
        val service = event.project?.takeUnless { it.isDisposed }?.serviceIfCreated<LibmanPendingService>() ?: return
        if (!service.hasAny() || !result.isPerformed) return
        when (ActionManager.getInstance().getId(action)) {
            SAVE_ALL -> service.applyAll()
            SAVE_DOCUMENT -> event.getData(CommonDataKeys.VIRTUAL_FILE)
                ?.takeIf { it.name.equals(ManifestPsi.FILE_NAME, ignoreCase = true) }
                ?.parent?.path?.let(service::apply)
        }
    }

    private companion object {
        const val SAVE_ALL = "SaveAll"
        const val SAVE_DOCUMENT = "SaveDocument"
    }
}

/** Drops the queues when a project closes, before its editors close (so no queue runs then). */
class LibmanProjectCloseListener : ProjectCloseListener {
    override fun projectClosing(project: Project) {
        project.serviceIfCreated<LibmanPendingService>()?.projectClosing()
    }
}
