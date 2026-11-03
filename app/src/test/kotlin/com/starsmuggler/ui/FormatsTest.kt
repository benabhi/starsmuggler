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
    fun `small numbers are plain`() {
        assertEquals("7", Formats.integer(7, Locale.forLanguageTag("es-AR")))
    }
}
