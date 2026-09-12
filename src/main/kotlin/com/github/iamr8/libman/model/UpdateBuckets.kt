package com.github.iamr8.libman.model

/** The kind of jump an update represents, with its chip/highlight color. */
enum class UpdateKind(val label: String, val color: SeverityColor) {
    PATCH("patch", SeverityColor.GREEN),
    MINOR("minor", SeverityColor.YELLOW),
    MAJOR("major", SeverityColor.RED),
    PRERELEASE("pre", SeverityColor.RED),
}

/** A concrete version the user can install, tagged with its kind. */
data class VersionCandidate(val version: SemVer, val kind: UpdateKind)

/**
 * The highest available update in each class, relative to the current version.
 * Any field may be null when no such update exists.
 */
data class UpdateBuckets(
    val patch: SemVer?,
    val minor: SemVer?,
    val major: SemVer?,
    val prerelease: SemVer?,
) {
    /** Candidates ordered patch, minor, major, prerelease (nulls dropped). */
    fun candidates(): List<VersionCandidate> = buildList {
        patch?.let { add(VersionCandidate(it, UpdateKind.PATCH)) }
        minor?.let { add(VersionCandidate(it, UpdateKind.MINOR)) }
        major?.let { add(VersionCandidate(it, UpdateKind.MAJOR)) }
        prerelease?.let { add(VersionCandidate(it, UpdateKind.PRERELEASE)) }
    }

    fun hasAny(): Boolean = patch != null || minor != null || major != null || prerelease != null

    /** Color for the current-version highlight: the most severe available jump. */
    fun highestColor(): SeverityColor = when {
        major != null || prerelease != null -> SeverityColor.RED
        minor != null -> SeverityColor.YELLOW
        patch != null -> SeverityColor.GREEN
        else -> SeverityColor.NONE
    }

    companion object {
        /** Parses [currentRaw]/[availableRaw] and computes the buckets. Unparseable versions are ignored. */
        fun compute(currentRaw: String?, availableRaw: List<String>, includePrerelease: Boolean): UpdateBuckets {
            val current = SemVer.parse(currentRaw) ?: return UpdateBuckets(null, null, null, null)
            val available = availableRaw.mapNotNull { SemVer.parse(it) }
            return compute(current, available, includePrerelease)
        }

        fun compute(current: SemVer, available: List<SemVer>, includePrerelease: Boolean): UpdateBuckets {
            val newer = available.filter { it > current }
            val stable = newer.filter { !it.isPrerelease }

            val patch = stable.filter { it.major == current.major && it.minor == current.minor }.maxOrNull()
            val minor = stable.filter { it.major == current.major && it.minor > current.minor }.maxOrNull()
            val major = stable.filter { it.major > current.major }.maxOrNull()

            val bestStable = listOfNotNull(patch, minor, major).maxOrNull()
            val prerelease = if (!includePrerelease) null else {
                newer.filter { it.isPrerelease }.maxOrNull()
                    ?.takeIf { bestStable == null || it > bestStable }
            }

            return UpdateBuckets(patch, minor, major, prerelease)
        }
    }
}
