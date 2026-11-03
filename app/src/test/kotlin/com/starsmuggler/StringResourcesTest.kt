package com.starsmuggler

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Test
import org.w3c.dom.Element
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The interface texts are complete (SMUG-6): the Spanish of values/ has no empty entries, every
 * plural has its forms, and any language added later (values-xx, SMUG-80) has exactly the same
 * keys and placeholders. A new values-xx folder is checked without changing this test.
 */
class StringResourcesTest {
    private val res = File("src/main/res")

    /** Texts of a values folder by key; a plural contributes one entry per quantity. */
    private fun texts(folder: File): Map<String, String> {
        val file = File(folder, "strings.xml")
        if (!file.exists()) return emptyMap()
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val result = linkedMapOf<String, String>()
        val strings = document.getElementsByTagName("string")
        for (i in 0 until strings.length) {
            val node = strings.item(i) as Element
            // Names marked translatable="false" are the same in every language.
            if (node.getAttribute("translatable") == "false") continue
            result[node.getAttribute("name")] = node.textContent
        }
        val plurals = document.getElementsByTagName("plurals")
        for (i in 0 until plurals.length) {
            val node = plurals.item(i) as Element
            val items = node.getElementsByTagName("item")
            for (j in 0 until items.length) {
                val item = items.item(j) as Element
                result["${node.getAttribute("name")}#${item.getAttribute("quantity")}"] = item.textContent
            }
        }
        val arrays = document.getElementsByTagName("string-array")
        for (i in 0 until arrays.length) {
            val node = arrays.item(i) as Element
            val items = node.getElementsByTagName("item")
            for (j in 0 until items.length) result["${node.getAttribute("name")}[$j]"] = items.item(j).textContent
        }
        return result
    }

    /** Language folders such as values-en or values-pt-rBR; not the ones for versions or screen sizes. */
    private val languages: List<File>
        get() = res.listFiles().orEmpty()
            .filter { it.name.matches(Regex("values-[a-z]{2}(-r[A-Z]{2})?")) && File(it, "strings.xml").exists() }

    private fun placeholders(text: String): List<String> = Regex("%\\d+\\$[sd]").findAll(text).map { it.value }.sorted().toList()

    private val base by lazy { texts(File(res, "values")) }

    @Test
    fun `the default texts are found`() {
        assertTrue(base.isNotEmpty(), "No strings under ${res.absolutePath}")
    }

    @Test
    fun `no text is empty`() {
        assertEquals(emptyList(), base.filterValues { it.isBlank() }.keys.toList())
    }

    @Test
    fun `every plural has the one and other forms`() {
        val plurals = base.keys.filter { '#' in it }.groupBy({ it.substringBefore('#') }, { it.substringAfter('#') })
        plurals.forEach { (name, quantities) ->
            assertTrue("one" in quantities && "other" in quantities, "$name has $quantities")
        }
    }

    @Test
    fun `every added language has the same keys`() {
        for (language in languages) {
            val translated = texts(language)
            assertEquals(emptySet(), base.keys - translated.keys, "Missing in ${language.name}")
            assertEquals(emptySet(), translated.keys - base.keys, "Only in ${language.name}")
        }
    }

    @Test
    fun `every added language keeps the placeholders`() {
        for (language in languages) {
            texts(language).forEach { (key, text) ->
                base[key]?.let { assertEquals(placeholders(it), placeholders(text), "$key in ${language.name}") }
            }
        }
    }
}
