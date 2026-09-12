package com.github.iamr8.libman.ui.intentions

import com.github.iamr8.libman.ui.LibraryEntryContext
import com.github.iamr8.libman.ui.ManifestPsi
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

/**
 * Base for the Alt+Enter actions offered on a library entry in `libman.json`.
 * Availability requires the caret to sit inside a library entry; subclasses that act on a version
 * are hidden for `filesystem` entries (local paths have no version).
 */
abstract class LibraryEntryIntention : IntentionAction {

    /** Whether this intention makes sense for a `filesystem` (path) entry. */
    protected open val filesystemApplicable: Boolean = false

    override fun getFamilyName(): String = "LibMan"

    // The CLI runs off the EDT and touches files on disk, not the PSI tree.
    override fun startInWriteAction(): Boolean = false

    // This action shells out to libman; never run it speculatively to render an Alt+Enter preview.
    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo =
        IntentionPreviewInfo.EMPTY

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        if (editor == null || file == null || !ManifestPsi.isManifest(file)) return false
        val ctx = ManifestPsi.libraryEntryAt(file, editor.caretModel.offset) ?: return false
        return filesystemApplicable || !ctx.isFilesystem
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        if (editor == null || file == null) return
        val ctx = ManifestPsi.libraryEntryAt(file, editor.caretModel.offset) ?: return
        // libman reads libman.json from disk; flush unsaved edits so it sees the current entry.
        FileDocumentManager.getInstance().saveDocument(editor.document)
        act(project, ctx)
    }

    protected abstract fun act(project: Project, ctx: LibraryEntryContext)
}
