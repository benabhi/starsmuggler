package com.starsmuggler.content

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The long texts of the game in one language (SMUG-13): rule sections (r201, r207a, ...) and
 * events (e001, ...), from `text/<language>/rules.json` and `events.json`.
 */
class GameTexts(val rules: Map<String, TextEntry>, val events: Map<String, TextEntry>) {
    fun rule(id: String): TextEntry? = rules[id]

    fun event(id: String): TextEntry? = events[id]

    /** A rule or event text by its id, as a reference in another text names it. */
    fun find(id: String): TextEntry? = if (id.startsWith("e")) events[id] else rules[id]

    companion object {
        /** A rule or event reference inside a text, such as r212b or e130. */
        val REFERENCE = Regex("\\b([re]\\d{3}[a-z]?)\\b")
    }
}

/** One rule section or event, ready to show. */
@Serializable
data class TextEntry(
    val title: String,
    val blocks: List<TextBlock>,
    /** The section a numbered subsection belongs to, such as r207 for r207a. */
    val parent: String? = null,
    /** The numbered subsections of a section, in order. */
    val children: List<String> = emptyList(),
    /** An event's duration as the event book words it, such as "toma 1 hora". */
    val duration: String? = null,
    /** The heading of the block a short event belongs to, such as e026-035. */
    val group: String? = null,
)

/** A block of a text. Inline text may hold **bold** spans and rule or event references. */
@Serializable
sealed interface TextBlock {
    @Serializable
    @SerialName("heading")
    data class Heading(val text: String) : TextBlock

    @Serializable
    @SerialName("paragraph")
    data class Paragraph(val text: String) : TextBlock

    @Serializable
    @SerialName("bullet")
    data class Bullet(val text: String, val level: Int = 0) : TextBlock

    /** A table; the first row is its header. */
    @Serializable
    @SerialName("table")
    data class Table(val rows: List<List<String>>) : TextBlock
}
