package com.github.iamr8.libman.ui

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

/**
 * Opens the completion popup in `libman.json` right after the version `@` and after a `/` in a
 * path ([LibmanCompletionContributor] decides what to offer). The PSI check runs later, on the
 * committed file, as the platform asks for this hook.
 */
class LibmanTypedHandler : TypedHandlerDelegate() {

    override fun checkAutoPopup(charTyped: Char, project: Project, editor: Editor, file: PsiFile): Result {
        if ((charTyped == '@' || charTyped == '/') && file.name.equals(ManifestPsi.FILE_NAME, ignoreCase = true)) {
            AutoPopupController.getInstance(project).scheduleAutoPopup(editor, CompletionType.BASIC) { ManifestPsi.isManifest(it) }
        }
        return Result.CONTINUE
    }
}
