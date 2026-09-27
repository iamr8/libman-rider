package com.github.iamr8.libman.ui

import com.github.iamr8.libman.model.DestinationDirs
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.SystemInfo
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet
import com.intellij.util.ProcessingContext

/**
 * Makes `destination` (of an entry or a `fileMappings` item) and `defaultDestination` in
 * `libman.json` folder paths, relative to the manifest's folder: the IDE's own path completion
 * (folder by folder), Ctrl+Click, and rename. The references are soft, since LibMan creates a
 * missing folder on restore, so an unknown path is not an error.
 */
class LibmanReferenceContributor : PsiReferenceContributor() {

    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(PlatformPatterns.psiElement(JsonStringLiteral::class.java), DestinationReferences)
    }

    private object DestinationReferences : PsiReferenceProvider() {

        override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
            val literal = element as? JsonStringLiteral ?: return PsiReference.EMPTY_ARRAY
            if (!ManifestPsi.isManifest(literal.containingFile) || !ManifestPsi.isDestinationValue(literal)) {
                return PsiReference.EMPTY_ARRAY
            }
            val range = ElementManipulators.getValueTextRange(literal)
            val set = object : FileReferenceSet(
                range.substring(literal.text), literal, range.startOffset, this, SystemInfo.isFileSystemCaseSensitive, false,
            ) {
                override fun isSoft(): Boolean = true

                // Folders only, without hidden and build output folders.
                override fun getReferenceCompletionFilter(): Condition<PsiFileSystemItem> =
                    Condition { it is PsiDirectory && !DestinationDirs.skip(it.name) }

                // Relative to the manifest's folder (schema: "relative folder path from this config file").
                override fun computeDefaultContexts(): Collection<PsiFileSystemItem> =
                    listOfNotNull(literal.containingFile.originalFile.containingDirectory)
            }
            return arrayOf<PsiReference>(*set.allReferences)
        }
    }
}
