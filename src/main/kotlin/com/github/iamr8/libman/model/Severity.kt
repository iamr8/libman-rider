package com.github.iamr8.libman.model

/**
 * Color bucket, matching the familiar SemVer legend:
 *   red = major or pre-release, yellow = minor, green = patch.
 * UI-framework-free; the UI maps these to JBColors for the version highlight and version chips.
 */
enum class SeverityColor { RED, YELLOW, GREEN, NONE }
