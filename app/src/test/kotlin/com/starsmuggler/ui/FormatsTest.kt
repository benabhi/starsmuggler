package com.starsmuggler.ui

import java.util.Locale
import org.junit.Test
import kotlin.test.assertEquals

class FormatsTest {
    @Test
    fun `numbers follow the locale grouping`() {
        assertEquals("12.000", Formats.integer(12000, Locale.forLanguageTag("es-AR")))
        assertEquals("12,000", Formats.integer(12000, Locale.forLanguageTag("en-US")))
    }

    @Test
    fun `adjustments show their sign`() {
        val es = Locale.forLanguageTag("es-AR")
        assertEquals("+1", Formats.signed(1, es))
        assertEquals("0", Formats.signed(0, es))
        assertEquals("-1", Formats.signed(-1, es).replace('\u2212', '-'))
    }

    @Test
    fun `small numbers are plain`() {
        assertEquals("7", Formats.integer(7, Locale.forLanguageTag("es-AR")))
    }
}
