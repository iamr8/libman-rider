package com.github.iamr8.libman.model

/**
 * A minimal SemVer 2.0.0 value: `major.minor.patch[-prerelease][+build]`.
 * Missing minor/patch default to 0. Build metadata is ignored for ordering.
 * Ordering follows the spec: a pre-release version is lower than the same core release, and
 * pre-release identifiers are compared field by field (numeric numerically, else ASCII).
 *
 * Pure and platform-free for unit testing.
 */
data class SemVer(
    val major: Int,
    val minor: Int,
    val patch: Int,
    /** Dot-separated pre-release identifiers, empty for a stable release. */
    val prerelease: List<String>,
    /** The original string, for display. */
    val raw: String,
) : Comparable<SemVer> {

    val isPrerelease: Boolean get() = prerelease.isNotEmpty()

    override fun compareTo(other: SemVer): Int {
        (major - other.major).let { if (it != 0) return it.coerceIn(-1, 1) }
        (minor - other.minor).let { if (it != 0) return it.coerceIn(-1, 1) }
        (patch - other.patch).let { if (it != 0) return it.coerceIn(-1, 1) }
        // Core equal: a release outranks a pre-release of the same core.
        if (prerelease.isEmpty() && other.prerelease.isEmpty()) return 0
        if (prerelease.isEmpty()) return 1
        if (other.prerelease.isEmpty()) return -1
        return comparePrerelease(prerelease, other.prerelease)
    }

    override fun toString(): String = raw

    companion object {
        /** Parses a version string, or returns `null` if the major component isn't numeric. */
        fun parse(version: String?): SemVer? {
            val trimmed = version?.trim().orEmpty()
            if (trimmed.isEmpty()) return null
            val noBuild = trimmed.substringBefore('+')
            val core = noBuild.substringBefore('-')
            val pre = noBuild.substringAfter('-', "").let {
                if (it.isEmpty()) emptyList() else it.split('.')
            }
            val parts = core.split('.')
            val nums = IntArray(3)
            for (i in 0 until minOf(3, parts.size)) {
                nums[i] = parts[i].toIntOrNull() ?: return null
            }
            return SemVer(nums[0], nums[1], nums[2], pre, trimmed)
        }

        private fun comparePrerelease(a: List<String>, b: List<String>): Int {
            val n = minOf(a.size, b.size)
            for (i in 0 until n) {
                val ai = a[i]; val bi = b[i]
                val an = ai.toIntOrNull(); val bn = bi.toIntOrNull()
                val cmp = when {
                    an != null && bn != null -> an.compareTo(bn)
                    an != null -> -1 // numeric identifiers are lower than alphanumeric
                    bn != null -> 1
                    else -> ai.compareTo(bi)
                }
                if (cmp != 0) return cmp.coerceIn(-1, 1)
            }
            return a.size.compareTo(b.size).coerceIn(-1, 1) // a larger set of fields is higher
        }
    }
}
