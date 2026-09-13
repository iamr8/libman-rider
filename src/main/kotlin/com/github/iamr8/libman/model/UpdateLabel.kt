package com.github.iamr8.libman.model

/** Text for an "update" chip. Pure and platform-free for unit testing. */
object UpdateLabel {

    /**
     * The severity kind (patch/minor/major) is shown only when there is more than one option, so
     * a single choice reads simply as "Update to 4.1.0", while several read as
     * "Update to 4.0.14 (patch)", "Update to 4.1.0 (minor)", "Update to 5.0.0 (major)".
     */
    fun chip(version: String, kind: String, single: Boolean): String =
        if (single) "Update to $version" else "Update to $version ($kind)"
}
