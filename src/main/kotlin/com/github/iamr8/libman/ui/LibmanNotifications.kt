package com.github.iamr8.libman.ui

import com.github.iamr8.libman.cli.CliFailures
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import java.awt.datatransfer.StringSelection

/** Balloon notifications for LibMan operations. User/environment failures live here, not the error log. */
object LibmanNotifications {

    const val GROUP_ID = "LibMan GUI"

    fun info(project: Project?, title: String, content: String) {
        notify(project, title, content, NotificationType.INFORMATION)
    }

    /** A user/environment failure: short [summary] as the balloon text, full [details] behind Copy Details. */
    fun failure(project: Project?, title: String, summary: String, details: String) {
        val n = group().createNotification(title, summary, NotificationType.WARNING)
        n.addAction(NotificationAction.createSimple("Copy Details") {
            CopyPasteManager.getInstance().setContents(StringSelection(details))
        })
        n.notify(project)
    }

    fun notInstalled(project: Project?) {
        info(project, "LibMan", CliFailures.NOT_INSTALLED)
    }

    /** Adds an action to an info balloon (e.g. an "Update" button after a check). */
    fun infoWithAction(project: Project?, title: String, content: String, actionText: String, action: () -> Unit) {
        val n = group().createNotification(title, content, NotificationType.INFORMATION)
        n.addAction(NotificationAction.createSimple(actionText) {
            action()
            n.expire()
        })
        n.notify(project)
    }

    private fun notify(project: Project?, title: String, content: String, type: NotificationType) {
        group().createNotification(title, content, type).notify(project)
    }

    private fun group() = NotificationGroupManager.getInstance().getNotificationGroup(GROUP_ID)
}
