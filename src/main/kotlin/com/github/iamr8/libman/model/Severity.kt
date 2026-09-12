package com.github.iamr8.libman.model

/**
 * How big a jump an available update is, derived from comparing the current and latest versions.
 * `libman` reports no severity of its own, so this is computed from the two version strings.
 */
enum class UpdateKind { NONE, PATCH, MINOR, MAJOR, PRERELEASE, UNKNOWN }

/**
 * Color bucket, matching the familiar SemVer legend:
 *   red = major or pre-release, yellow = minor, green = patch.
 * UI-framework-free so it can be unit-tested; the UI maps these to JBColors.
 */
enum class SeverityColor { RED, YELLOW, GREEN, NONE }

/** True if a version string carries a pre-release label (e.g. `4.1.0-beta.1`). */
fun isPrerelease(version: String?): Boolean = version?.substringBefore('+')?.contains('-') == true

/**
 * Classifies the jump from [current] to [latest]. A pre-release [latest] is always [UpdateKind.PRERELEASE];
 * otherwise the first differing numeric component (major, then minor, then patch) decides.
 * Unparseable input is [UpdateKind.UNKNOWN]; equal versions are [UpdateKind.NONE].
 */
fun computeUpdateKind(current: String?, latest: String?): UpdateKind {
    if (isPrerelease(latest)) return UpdateKind.PRERELEASE
    val cur = parseSemver(current) ?: return UpdateKind.UNKNOWN
    val lat = parseSemver(latest) ?: return UpdateKind.UNKNOWN
    return when {
        lat[0] != cur[0] -> UpdateKind.MAJOR
        lat[1] != cur[1] -> UpdateKind.MINOR
        lat[2] != cur[2] -> UpdateKind.PATCH
        else -> UpdateKind.NONE
    }
}

/** Maps an [UpdateKind] to its legend color. */
fun severityColor(kind: UpdateKind): SeverityColor = when (kind) {
    UpdateKind.PRERELEASE, UpdateKind.MAJOR, UpdateKind.UNKNOWN -> SeverityColor.RED
    UpdateKind.MINOR -> SeverityColor.YELLOW
    UpdateKind.PATCH -> SeverityColor.GREEN
    UpdateKind.NONE -> SeverityColor.NONE
}

/** Convenience: color for a current -> latest jump. */
fun severityColor(current: String?, latest: String?): SeverityColor = severityColor(computeUpdateKind(current, latest))

/**
 * Parses `major[.minor[.patch]]` into a 3-int array, ignoring any pre-release/build suffix.
 * Missing components default to 0. Returns `null` if the major part isn't numeric.
 */
private fun parseSemver(version: String?): IntArray? {
    val core = version?.trim()?.substringBefore('-')?.substringBefore('+') ?: return null
    if (core.isEmpty()) return null
    val parts = core.split('.')
    val out = intArrayOf(0, 0, 0)
    for (i in 0 until minOf(3, parts.size)) {
        out[i] = parts[i].toIntOrNull() ?: return null
    }
    return out
}
