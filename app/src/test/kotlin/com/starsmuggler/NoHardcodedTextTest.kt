package com.starsmuggler

import java.io.File
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * No visible text is written in the code (SMUG-6, PRD section 10): every text comes from
 * strings.xml or the text assets, so another language only adds resources. Lint cannot see
 * literals passed to Compose, so this test reads the sources instead.
 */
class NoHardcodedTextTest {
    private val sources = File("src/main/kotlin")

    // Rule and event ids (r233, r203a, e001) are references, not text to translate.
    private val ruleId = Regex("[re]\\d{3}[a-z]?")

    // A line that ends with this marker holds a technical string that never reaches the screen.
    private val marker = "// not-ui"

    private val literal = Regex("\"((?:[^\"\\\\]|\\\\.)*)\"")
    private val blockComment = Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL)
    private val lineComment = Regex("//.*")

    /** The string literals of a Kotlin source that hold letters, with their line numbers. */
    fun textLiterals(source: String): List<Pair<Int, String>> {
        // Blank the comments but keep the line breaks, so line numbers stay right.
        val code = blockComment.replace(source) { it.value.replace(Regex("[^\\n]"), " ") }
        return code.lines().flatMapIndexed { index, line ->
            if (line.trimStart().startsWith("import ") || line.trimStart().startsWith("package ")) return@flatMapIndexed emptyList()
            if (source.lines()[index].trimEnd().endsWith(marker)) return@flatMapIndexed emptyList()
            literal.findAll(lineComment.replace(line, ""))
                .map { it.groupValues[1] }
                .filter { text -> text.any { it.isLetter() } && !ruleId.matches(text) }
                .map { index + 1 to it }
                .toList()
        }
    }

    @Test
    fun `the sources are found`() {
        assertTrue(sources.walk().any { it.extension == "kt" }, "No Kotlin sources under ${sources.absolutePath}")
    }

    @Test
    fun `no visible text is written in the code`() {
        val found = sources.walk().filter { it.extension == "kt" }.flatMap { file ->
            textLiterals(file.readText()).map { (line, text) -> "${file.relativeTo(sources)}:$line \"$text\"" }
        }.toList()
        assertEquals(emptyList(), found, "Move these texts to res/values/strings.xml")
    }

    @Test
    fun `the check catches text and lets ids and comments through`() {
        val sample = """
            // A comment with "words" is fine.
            /** So is "this". */
            Text("Terminar el día")
            ActionCard(title = stringResource(R.string.end_day), ruleRef = "r233")
            RecordLine(label, "4")
            val tag = "debug" // not-ui
        """.trimIndent()
        assertEquals(listOf(3 to "Terminar el día"), textLiterals(sample))
    }
}
