package com.github.iamr8.libman.ui

import com.github.iamr8.libman.model.LibraryId
import com.intellij.json.psi.JsonArray
import com.intellij.json.psi.JsonFile
import com.intellij.json.psi.JsonObject
import com.intellij.json.psi.JsonProperty
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

/** The library entry the caret sits inside, resolved from `libman.json` PSI. */
data class LibraryEntryContext(
    /** The library id, parsed from the entry's `library` value. */
    val id: LibraryId,
    /** The effective provider (the entry's own `provider`, else the manifest `defaultProvider`). */
    val provider: String?,
    /** Absolute path of the folder holding `libman.json` (the CLI's working directory). */
    val manifestDir: String,
) {
    /** A filesystem-provider entry is a local path with no version to check or update. */
    val isFilesystem: Boolean
        get() = provider?.trim()?.lowercase() == LibraryId.FILESYSTEM_PROVIDER
}

/** PSI helpers for the `libman.json` manifest. All calls must run inside a read action. */
object ManifestPsi {

    const val FILE_NAME = "libman.json"

    fun isManifest(file: PsiFile?): Boolean =
        file is JsonFile && file.name.equals(FILE_NAME, ignoreCase = true)

    /**
     * Resolves the library entry containing [offset], or `null` if the caret isn't inside one.
     * A library entry is a [JsonObject] that is an element of the top-level `libraries` array.
     */
    fun libraryEntryAt(file: PsiFile, offset: Int): LibraryEntryContext? {
        if (!isManifest(file)) return null
        val manifestDir = file.virtualFile?.parent?.path ?: return null

        val obj = enclosingLibraryObject(file.findElementAt(offset)) ?: return null
        val library = stringValue(obj, "library") ?: return null

        val entryProvider = stringValue(obj, "provider")
        val defaultProvider = (file as? JsonFile)?.let { defaultProvider(it) }
        val provider = entryProvider ?: defaultProvider

        return LibraryEntryContext(LibraryId.parse(library, provider), provider, manifestDir)
    }

    /** Walks up from [start] to the nearest [JsonObject] that is an item of the `libraries` array. */
    private fun enclosingLibraryObject(start: PsiElement?): JsonObject? {
        var e: PsiElement? = start
        while (e != null) {
            if (e is JsonObject) {
                val array = e.parent as? JsonArray
                val property = array?.parent as? JsonProperty
                if (property?.name == "libraries") return e
            }
            e = e.parent
        }
        return null
    }

    private fun defaultProvider(file: JsonFile): String? {
        val root = file.topLevelValue as? JsonObject ?: return null
        return stringValue(root, "defaultProvider")
    }

    private fun stringValue(obj: JsonObject, propertyName: String): String? =
        (obj.findProperty(propertyName)?.value as? JsonStringLiteral)?.value
}
