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
