package com.github.iamr8.libman.model

/**
 * Word-wraps text to at most [maxLines] lines of about [maxWidth] characters, truncating any
 * remainder with an ellipsis on the last line. Pure, so it's unit-testable independently of the
 * inlay renderer.
 */
object TextWrap {

    fun wrap(text: String?, maxWidth: Int = 80, maxLines: Int = 3): List<String> {
        val clean = text?.trim()?.replace(Regex("\\s+"), " ").orEmpty()
        if (clean.isEmpty() || maxLines <= 0) return emptyList()

        val words = clean.split(' ')
        val lines = mutableListOf<String>()
        val cur = StringBuilder()
        var idx = 0

        while (idx < words.size && lines.size < maxLines) {
            val w = words[idx]
            when {
                cur.isEmpty() -> { cur.append(w); idx++ }
                cur.length + 1 + w.length <= maxWidth -> { cur.append(' ').append(w); idx++ }
                else -> { lines.add(cur.toString()); cur.setLength(0) }
            }
        }
        if (lines.size < maxLines && cur.isNotEmpty()) { lines.add(cur.toString()); cur.setLength(0) }

        val remaining = idx < words.size || cur.isNotEmpty()
        if (remaining && lines.isNotEmpty()) {
            val last = lines.removeAt(lines.size - 1)
            lines.add(ellipsize(last, maxWidth))
        }
        return lines
    }

    private fun ellipsize(line: String, maxWidth: Int): String =
        if (line.length < maxWidth) "$line…" else line.substring(0, maxWidth - 1).trimEnd() + "…"
}
