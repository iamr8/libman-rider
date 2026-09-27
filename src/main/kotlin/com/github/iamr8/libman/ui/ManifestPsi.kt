package com.github.iamr8.libman.ui

import com.github.iamr8.libman.model.LibraryId
import com.github.iamr8.libman.model.ManifestDefaults
import com.intellij.json.psi.JsonArray
import com.intellij.json.psi.JsonFile
import com.intellij.json.psi.JsonObject
import com.intellij.json.psi.JsonProperty
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.openapi.util.TextRange
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

    /** Every library entry object in the top-level `libraries` array. */
    fun libraryObjects(file: PsiFile): List<JsonObject> {
        if (!isManifest(file)) return emptyList()
        val root = (file as? JsonFile)?.topLevelValue as? JsonObject ?: return emptyList()
        val array = root.findProperty("libraries")?.value as? JsonArray ?: return emptyList()
        return array.valueList.filterIsInstance<JsonObject>()
    }

    /** Resolves the context for a specific library entry object. */
    fun contextOf(obj: JsonObject, file: PsiFile): LibraryEntryContext? {
        val manifestDir = file.virtualFile?.parent?.path ?: return null
        val library = stringValue(obj, "library") ?: return null
        val provider = stringValue(obj, "provider") ?: (file as? JsonFile)?.let { defaultProvider(it) }
        return LibraryEntryContext(LibraryId.parse(library, provider), provider, manifestDir)
    }

    /** The entry's own `provider`, else the manifest `defaultProvider` of [file]. */
    fun effectiveProvider(obj: JsonObject, file: PsiFile): String? =
        ManifestDefaults.provider(stringValue(obj, "provider"), (file as? JsonFile)?.let { defaultProvider(it) })

    /** The entry's own `destination`, else the manifest `defaultDestination` of [file] (tokens expanded). */
    fun effectiveDestination(obj: JsonObject, file: PsiFile, id: LibraryId): String? {
        val root = (file as? JsonFile)?.topLevelValue as? JsonObject
        return ManifestDefaults.destination(stringValue(obj, "destination"), root?.let { stringValue(it, "defaultDestination") }, id.name, id.version)
    }

    /**
     * True for a `destination` value of a library entry or a `fileMappings` item, or the manifest
     * `defaultDestination`. Works on a completion copy too (it checks the literal's own file).
     */
    fun isDestinationValue(literal: JsonStringLiteral): Boolean {
        val property = literal.parent as? JsonProperty ?: return false
        if (property.value != literal) return false
        val owner = property.parent as? JsonObject ?: return false
        return when (property.name) {
            "destination" -> isLibraryEntry(owner) || mappingEntry(owner) != null
            "defaultDestination" -> owner == (literal.containingFile as? JsonFile)?.topLevelValue
            else -> false
        }
    }

    /** The library entry of a `fileMappings` item (schema 3.0), or null when [obj] is not one. */
    fun mappingEntry(obj: JsonObject): JsonObject? {
        val array = obj.parent as? JsonArray ?: return null
        val property = array.parent as? JsonProperty ?: return null
        if (property.name != "fileMappings") return null
        return (property.parent as? JsonObject)?.takeIf { isLibraryEntry(it) }
    }

    /** A `fileMappings` item's `root` (inside the library), or null for the library root. */
    fun mappingRoot(mapping: JsonObject): String? = stringValue(mapping, "root")

    /** A `fileMappings` item's own `destination`, or null (then the library's destination applies). */
    fun mappingDestination(mapping: JsonObject): String? = stringValue(mapping, "destination")

    /** The `files` values of an entry, each with the range of its text (inside the quotes). */
    fun fileValues(obj: JsonObject): List<Pair<String, TextRange>> {
        val array = obj.findProperty("files")?.value as? JsonArray ?: return emptyList()
        return array.valueList.filterIsInstance<JsonStringLiteral>().map { literal ->
            val r = literal.textRange
            literal.value to TextRange(r.startOffset + 1, maxOf(r.startOffset + 1, r.endOffset - 1))
        }
    }

    /** Resolves the library entry containing [offset], or `null` if the caret isn't inside one. */
    fun libraryEntryAt(file: PsiFile, offset: Int): LibraryEntryContext? {
        if (!isManifest(file)) return null
        val obj = enclosingLibraryObject(file.findElementAt(offset)) ?: return null
        return contextOf(obj, file)
    }

    /** True if [obj] is a direct element of the top-level `libraries` array. */
    fun isLibraryEntry(obj: JsonObject): Boolean {
        val array = obj.parent as? JsonArray ?: return false
        val property = array.parent as? JsonProperty ?: return false
        return property.name == "libraries"
    }

    /** The `library` value string literal of an entry, if present. */
    fun libraryValueLiteral(obj: JsonObject): JsonStringLiteral? =
        obj.findProperty("library")?.value as? JsonStringLiteral

    /**
     * TextRange of just the version substring inside the `library` value (the part after the last
     * `@`), or `null` when the entry has no version or is a filesystem path.
     */
    fun versionRange(obj: JsonObject, ctx: LibraryEntryContext): TextRange? {
        if (ctx.isFilesystem || ctx.id.version == null) return null
        val literal = libraryValueLiteral(obj) ?: return null
        val content = ctx.id.raw // the raw library value, e.g. "jquery@3.6.0"
        val at = content.lastIndexOf('@')
        if (at <= 0) return null
        // literal.textRange covers the surrounding quotes; +1 skips the opening quote. libman
        // library ids contain no escapes, so content offsets map directly onto the literal text.
        val contentStart = literal.textRange.startOffset + 1
        return TextRange(contentStart + at + 1, contentStart + content.length)
    }

    /**
     * TextRange of the name portion of the `library` value (before the version's `@`), or the whole
     * value when there is no version. Used to anchor the description hover tooltip on the name.
     */
    fun nameRange(obj: JsonObject, ctx: LibraryEntryContext): TextRange? {
        val literal = libraryValueLiteral(obj) ?: return null
        val content = ctx.id.raw
        val contentStart = literal.textRange.startOffset + 1
        val at = if (ctx.id.version != null && !ctx.isFilesystem) content.lastIndexOf('@') else -1
        val nameEnd = if (at > 0) at else content.length
        return TextRange(contentStart, contentStart + nameEnd)
    }

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
