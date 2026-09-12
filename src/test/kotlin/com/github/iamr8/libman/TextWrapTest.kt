package com.github.iamr8.libman

import com.github.iamr8.libman.model.TextWrap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextWrapTest {

    @Test fun `empty or blank yields no lines`() {
        assertEquals(emptyList<String>(), TextWrap.wrap(null))
        assertEquals(emptyList<String>(), TextWrap.wrap("   "))
    }

    @Test fun `short text stays one line`() {
        assertEquals(listOf("JavaScript library for DOM operations"),
            TextWrap.wrap("JavaScript library for DOM operations", maxWidth = 80, maxLines = 3))
    }

    @Test fun `whitespace is collapsed`() {
        assertEquals(listOf("a b c"), TextWrap.wrap("a   b\n c ", maxWidth = 80))
    }

    @Test fun `wraps onto multiple lines`() {
        val lines = TextWrap.wrap("one two three four five", maxWidth = 9, maxLines = 3)
        assertEquals(listOf("one two", "three", "four five"), lines)
    }

    @Test fun `truncates beyond maxLines with ellipsis`() {
        val lines = TextWrap.wrap("aa bb cc dd ee ff gg hh", maxWidth = 5, maxLines = 2)
        assertEquals(2, lines.size)
        assertTrue(lines.last(), lines.last().endsWith("…"))
    }

    @Test fun `no ellipsis when it all fits in maxLines`() {
        val lines = TextWrap.wrap("aa bb cc", maxWidth = 5, maxLines = 3)
        assertTrue(lines.none { it.endsWith("…") })
    }

    @Test fun `respects maxLines cap`() {
        val lines = TextWrap.wrap("a b c d e f g h i j k l", maxWidth = 1, maxLines = 3)
        assertEquals(3, lines.size)
    }
}
