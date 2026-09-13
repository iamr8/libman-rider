package com.github.iamr8.libman

import com.intellij.icons.AllIcons
import com.intellij.json.JsonLanguage
import com.intellij.openapi.fileTypes.LanguageFileType
import javax.swing.Icon

/**
 * A JSON-language file type bound to the exact name `libman.json`.
 *
 * Its only purpose is discovery: the JetBrains Marketplace indexes the `fileNames` of this type, so
 * the IDE's plugin advertiser suggests installing this plugin when a user opens a `libman.json`
 * (the advertiser matches the exact file name even though JSON is a known type).
 *
 * The language is JSON, so the file keeps full JSON parsing, highlighting and schema support, and
 * every JSON-language annotator/inlay in this plugin still fires (our checks match by `JsonFile` +
 * file name, never by file type). Registered via `<fileType fileNames="libman.json">` in plugin.xml
 * with `fieldName="INSTANCE"` — the Kotlin `object` exposes the singleton as a static `INSTANCE`.
 *
 * This is a secondary JSON-language type; JsonFileType stays the primary type for the JSON language,
 * so the plugin.xml bean sets no `language` attribute (that would claim the primary role).
 */
object LibManJsonFileType : LanguageFileType(JsonLanguage.INSTANCE) {
    override fun getName(): String = "LibMan Configuration"
    override fun getDescription(): String = "LibMan client-side library manifest (libman.json)"
    override fun getDefaultExtension(): String = "json"
    override fun getIcon(): Icon = AllIcons.FileTypes.Json
}
