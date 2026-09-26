package com.github.iamr8.libman.ui

import com.github.iamr8.libman.model.PendingChange
import com.github.iamr8.libman.model.PendingChanges
import com.github.iamr8.libman.provider.LibmanCatalogService
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.EditorNotifications
import com.intellij.util.concurrency.ThreadingAssertions
import java.util.concurrent.ConcurrentHashMap

/**
 * The queued Update/Remove clicks, per manifest folder (the CLI's working directory). Nothing runs
 * on a click; a queue runs on an explicit save ([LibmanSaveListener]), when its `libman.json`
 * closes ([LibmanFileOpenListener]), or with the banner's "Apply now" ([LibmanPendingBanner]).
 *
 * In memory only: pending changes are dropped when the project closes. Reads are thread-safe (the
 * inlays read off the EDT); [apply] runs on the EDT.
 */
@Service(Service.Level.PROJECT)
class LibmanPendingService(private val project: Project) {

    private val queues = ConcurrentHashMap<String, List<PendingChange>>()

    @Volatile
    private var closing = false

    /** The queued change for library [name] of the manifest in [manifestDir], or null. */
    fun pending(manifestDir: String, name: String): PendingChange? =
        queues[manifestDir]?.let { PendingChanges.find(it, name) }

    /** Number of queued changes for the manifest in [manifestDir]. */
    fun count(manifestDir: String): Int = queues[manifestDir]?.size ?: 0

    fun hasAny(): Boolean = queues.isNotEmpty()

    fun enqueue(manifestDir: String, change: PendingChange) {
        if (closing) return
        queues.compute(manifestDir) { _, q -> PendingChanges.add(q.orEmpty(), change) }
        changed()
    }

    /** Undo the queued change for library [name]. */
    fun cancel(manifestDir: String, name: String) {
        queues.computeIfPresent(manifestDir) { _, q -> PendingChanges.cancel(q, name).ifEmpty { null } }
        changed()
    }

    /** Drop every queued change of the manifest without running it. */
    fun discard(manifestDir: String) {
        queues.remove(manifestDir)
        changed()
    }

    /** Run every queue of the project (Save All). */
    fun applyAll() {
        queues.keys.toList().forEach(::apply)
    }

    /**
     * Run the queue of the manifest in [manifestDir] as one background task. Changes that do not
     * run (the task stopped early, or another operation holds the manifest) stay queued.
     */
    fun apply(manifestDir: String) {
        ThreadingAssertions.assertEventDispatchThread()
        if (closing) return
        val changes = queues.remove(manifestDir) ?: return
        // libman rewrites libman.json; save it first so an unsaved edit is neither lost nor in conflict.
        saveManifest(manifestDir)
        val started = LibmanOps.applyPending(project, manifestDir, changes) { notRun -> requeue(manifestDir, notRun) }
        if (!started) {
            requeue(manifestDir, changes)
            LibmanNotifications.info(project, "LibMan", "Another LibMan operation is running on this manifest. The changes stay pending.")
        }
        changed()
    }

    /** The project is closing: drop the queues and ignore the file-close events that follow. */
    fun projectClosing() {
        closing = true
        queues.clear()
    }

    private fun requeue(manifestDir: String, notRun: List<PendingChange>) {
        if (notRun.isEmpty() || closing) return
        queues.compute(manifestDir) { _, q -> PendingChanges.requeue(q.orEmpty(), notRun) }
        changed()
    }

    private fun saveManifest(manifestDir: String) {
        val dir = LocalFileSystem.getInstance().findFileByPath(manifestDir) ?: return
        val manifest = dir.children.firstOrNull { it.name.equals(ManifestPsi.FILE_NAME, ignoreCase = true) } ?: return
        val fdm = FileDocumentManager.getInstance()
        fdm.getCachedDocument(manifest)?.takeIf { fdm.isDocumentUnsaved(it) }?.let(fdm::saveDocument)
    }

    // Re-render the banner and the action rows (the inlay needs the stamp reset in requestRefresh).
    private fun changed() {
        if (project.isDisposed) return
        EditorNotifications.getInstance(project).updateAllNotifications()
        LibmanCatalogService.getInstance(project).requestRefresh()
    }

    companion object {
        fun getInstance(project: Project): LibmanPendingService = project.service()
    }
}
