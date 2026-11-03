package com.starsmuggler.content

import java.io.File
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The whole content holds together (SMUG-16); see [ContentValidator]. */
class ContentValidatorTest {
    private val assets = File("src/main/assets")
    private val files = ContentSource { path -> File(assets, path).readText() }

    @Test
    fun `the shipped content has no problems`() {
        for (language in TextLanguage.supported) {
            val problems = ContentValidator.validate(ContentLoader(files).load(language))
            assertEquals(emptyList(), problems.map { it.toString() }, "Content problems in '$language'")
        }
    }

    @Test
    fun `every known erratum is still in the books`() {
        // An entry that no longer matches anything should be removed, so the list stays honest.
        val content = ContentLoader(files).load()
        val texts = content.texts
        for (known in ContentValidator.KNOWN.keys) {
            val (where, ref) = known.split(" -> ")
            val entry = texts.rule(where) ?: texts.event(where)
            val found = if (entry != null) {
                entry.toString().contains(ref)
            } else {
                content.game.tables[where.removePrefix("table ")]?.toString()?.contains(ref) == true
            }
            assertTrue(found, "Known erratum '$known' no longer appears")
        }
    }

    @Test
    fun `a broken reference is reported`() {
        val broken = ContentSource { path ->
            val text = files.read(path)
            if (path == ContentLoader.SECTOR) text.replace("\"e090\"", "\"e299\"") else text
        }
        val problems = ContentValidator.validate(ContentLoader(broken).load())
        assertEquals(listOf("system regari: names e299, which has no text"), problems.map { it.toString() })
    }

    @Test
    fun `a missing name is reported`() {
        val broken = ContentSource { path ->
            val text = files.read(path)
            if (path.endsWith(ContentLoader.NAMES)) text.replace("\"Cubro\"", "\"\"") else text
        }
        val problems = ContentValidator.validate(ContentLoader(broken).load())
        assertEquals(listOf("system: no Spanish name for 'cubro'"), problems.map { it.toString() })
    }

    @Test
    fun `every language folder has the same texts`() {
        val spanish = ContentLoader(files).load(TextLanguage.DEFAULT).texts
        for (language in TextLanguage.supported) {
            val texts = ContentLoader(files).load(language).texts
            assertEquals(spanish.rules.keys, texts.rules.keys, "Rule texts in '$language'")
            assertEquals(spanish.events.keys, texts.events.keys, "Event texts in '$language'")
        }
    }
}
