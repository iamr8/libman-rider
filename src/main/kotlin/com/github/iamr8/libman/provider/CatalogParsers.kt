package com.github.iamr8.libman.provider

import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Pure parsers for each provider's catalog JSON. Platform-free (Gson only) for unit testing.
 * Each returns `null` if the payload can't be read as the expected shape.
 */
object CatalogParsers {

    /** cdnjs: `{ "description": "...", "versions": ["4.0.0", ...] }`. */
    fun parseCdnjs(json: String): LibInfo? {
        val obj = root(json) ?: return null
        val versions = obj.getAsJsonArray("versions")?.mapNotNull { it.asStringOrNull() } ?: return null
        return LibInfo(versions, obj.string("description"))
    }

    /** npm registry (unpkg, jsDelivr npm): `{ "description": "...", "versions": { "5.3.8": {...} } }`. */
    fun parseNpm(json: String): LibInfo? {
        val obj = root(json) ?: return null
        val versionsObj = obj.getAsJsonObject("versions") ?: return null
        return LibInfo(versionsObj.keySet().toList(), obj.string("description"))
    }

    /** jsDelivr data API: `{ "versions": [ { "version": "5.3.8" }, ... ] }` (no description). */
    fun parseJsdelivr(json: String): LibInfo? {
        val obj = root(json) ?: return null
        val versions = obj.getAsJsonArray("versions")
            ?.mapNotNull { (it as? JsonObject)?.string("version") } ?: return null
        return LibInfo(versions, null)
    }

    /** cdnjs search: `{ "results": [ { "name": "jquery", "version": "4.0.0", "description": "..." } ] }`. */
    fun parseCdnjsSearch(json: String): List<LibrarySuggestion>? {
        val results = root(json)?.getAsJsonArray("results") ?: return null
        return results.mapNotNull { (it as? JsonObject)?.suggestion() }
    }

    /** npm search: `{ "objects": [ { "package": { "name": "jquery", "version": "...", "description": "..." } } ] }`. */
    fun parseNpmSearch(json: String): List<LibrarySuggestion>? {
        val objects = root(json)?.getAsJsonArray("objects") ?: return null
        return objects.mapNotNull { ((it as? JsonObject)?.get("package") as? JsonObject)?.suggestion() }
    }

    /** cdnjs files of one version: `{ "files": ["jquery.js", "jquery.min.js"] }`. */
    fun parseCdnjsFiles(json: String): List<String>? =
        root(json)?.getAsJsonArray("files")?.mapNotNull { it.asStringOrNull() }

    /**
     * jsDelivr flat file list (npm and GitHub): `{ "files": [ { "name": "/dist/jquery.js" } ] }`.
     * Names are returned without the leading `/`, the form `libman.json` uses.
     */
    fun parseJsdelivrFiles(json: String): List<String>? =
        root(json)?.getAsJsonArray("files")
            ?.mapNotNull { (it as? JsonObject)?.string("name")?.removePrefix("/") }

    private fun JsonObject.suggestion(): LibrarySuggestion? =
        string("name")?.let { LibrarySuggestion(it, string("version"), string("description")) }

    private fun root(json: String): JsonObject? = try {
        JsonParser.parseString(json).takeIf { it.isJsonObject }?.asJsonObject
    } catch (e: Exception) {
        null
    }

    private fun JsonObject.string(name: String): String? =
        get(name)?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }

    private fun com.google.gson.JsonElement.asStringOrNull(): String? =
        takeIf { it.isJsonPrimitive }?.asString
}
