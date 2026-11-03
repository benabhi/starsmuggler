package com.starsmuggler.content

import org.junit.Test
import kotlin.test.assertEquals

class TextLanguageTest {
    @Test
    fun `spanish tags resolve to spanish`() {
        assertEquals("es", TextLanguage.resolve("es-AR"))
        assertEquals("es", TextLanguage.resolve("es_ES"))
        assertEquals("es", TextLanguage.resolve("ES"))
    }

    @Test
    fun `languages without texts fall back to the default`() {
        assertEquals(TextLanguage.DEFAULT, TextLanguage.resolve("en-US"))
        assertEquals(TextLanguage.DEFAULT, TextLanguage.resolve(""))
    }

    @Test
    fun `long texts live in one folder per language`() {
        assertEquals("text/es", TextLanguage.assetDir("es-AR"))
        assertEquals("text/es", TextLanguage.assetDir("fr"))
    }

    @Test
    fun `the default language is supported`() {
        assertEquals(TextLanguage.DEFAULT, TextLanguage.supported.first())
    }
}
