package com.starsmuggler.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.starsmuggler.content.GameTexts
import com.starsmuggler.content.TextBlock
import com.starsmuggler.ui.theme.Ink
import com.starsmuggler.ui.theme.InkFaded
import com.starsmuggler.ui.theme.RocketRed
import com.starsmuggler.ui.theme.TeletypeStyles

/**
 * A rule or event text as the books print it: headings, paragraphs, lists and tables, with
 * **bold** spans and r###/e### references the player can tap (SMUG-13).
 */
@Composable
fun GameText(
    blocks: List<TextBlock>,
    onReference: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** Whether a reference has a text to go to; the others, such as errata of the book, stay plain. */
    exists: (String) -> Boolean = { true },
) {
    val links = Links(onReference, exists)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (block in blocks) {
            when (block) {
                is TextBlock.Heading -> Text(
                    richText(block.text, links),
                    style = MaterialTheme.typography.titleSmall,
                    color = RocketRed,
                    modifier = Modifier.padding(top = 4.dp),
                )
                is TextBlock.Paragraph -> Text(richText(block.text, links), style = MaterialTheme.typography.bodyMedium, color = Ink)
                is TextBlock.Bullet -> Row(Modifier.padding(start = (12 * block.level).dp)) {
                    if (!block.text.first().isDigit()) {
                        Text(BULLET, style = MaterialTheme.typography.bodyMedium, color = RocketRed, modifier = Modifier.padding(end = 6.dp))
                    }
                    Text(richText(block.text, links), style = MaterialTheme.typography.bodyMedium, color = Ink)
                }
                is TextBlock.Table -> TextTable(block, links)
            }
        }
    }
}

@Composable
private fun TextTable(table: TextBlock.Table, links: Links) = BoxWithConstraints {
    // Each column is as wide as its longest cell, up to MAX_COLUMN; longer cells wrap. When the
    // table is wider than the screen, the widest columns narrow to fit (SMUG-81). Tables that
    // still do not fit, such as r235 with eleven columns of numbers, scroll sideways.
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val columns = table.rows.maxOf { it.size }
    val natural = (0 until columns).map { column ->
        val longest = table.rows.mapNotNull { it.getOrNull(column) }.maxOf { cell ->
            measurer.measure(cell.replace(BOLD_MARK, ""), TeletypeStyles.small, softWrap = false).size.width
        }
        with(density) { longest.toDp() + CELL_PADDING * 2 }.coerceIn(MIN_COLUMN, MAX_COLUMN)
    }
    val widths = fitColumns(natural, maxWidth - TABLE_BORDER * 2)
    Column(Modifier.horizontalScroll(rememberScrollState())) {
        table.caption?.let { caption ->
            // The caption names the columns after the first one, as the book prints it above them.
            Row {
                Spacer(Modifier.width(widths.first()))
                Text(
                    caption,
                    style = TeletypeStyles.small,
                    color = RocketRed,
                    modifier = Modifier.width(widths.drop(1).fold(0.dp) { a, b -> a + b }).padding(horizontal = CELL_PADDING, vertical = 3.dp),
                )
            }
        }
        Column(Modifier.border(1.dp, InkFaded)) {
            table.rows.forEachIndexed { index, row ->
                Row(Modifier.height(IntrinsicSize.Min)) {
                    for (column in 0 until columns) {
                        Text(
                            richText(row.getOrElse(column) { "" }, links),
                            style = TeletypeStyles.small,
                            color = if (index == 0) RocketRed else Ink,
                            modifier = Modifier
                                .width(widths[column])
                                .fillMaxHeight()
                                .border(0.5.dp, InkFaded)
                                .padding(horizontal = CELL_PADDING, vertical = 3.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * The column [widths] capped at one common width, the largest that makes them fit in
 * [available], so only the widest columns narrow; never below [MIN_FITTED_COLUMN].
 */
private fun fitColumns(widths: List<Dp>, available: Dp): List<Dp> {
    if (widths.fold(0.dp) { a, b -> a + b } <= available) return widths
    var low = MIN_FITTED_COLUMN
    var high = widths.max()
    repeat(FIT_STEPS) {
        val cap = (low + high) / 2
        if (widths.fold(0.dp) { a, w -> a + minOf(w, cap) } <= available) low = cap else high = cap
    }
    return widths.map { minOf(it, low) }
}

private const val FIT_STEPS = 20
private val MIN_FITTED_COLUMN = 72.dp
private val TABLE_BORDER = 1.dp
private val CELL_PADDING = 4.dp
private val MIN_COLUMN = 32.dp
private val MAX_COLUMN = 200.dp
private val BOLD_MARK = Regex("\\*\\*")
private const val BULLET = "•"
private val BOLD = Regex("\\*\\*(.+?)\\*\\*")

/** [text] with its **bold** spans and its references made tappable. */
private fun richText(text: String, links: Links): AnnotatedString = buildAnnotatedString {
    var at = 0
    for (bold in BOLD.findAll(text)) {
        appendReferences(text.substring(at, bold.range.first), links)
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { appendReferences(bold.groupValues[1], links) }
        at = bold.range.last + 1
    }
    appendReferences(text.substring(at), links)
}

/** What a tap on a reference does, and which references have a text to go to. */
private class Links(val onReference: (String) -> Unit, val exists: (String) -> Boolean)

private fun AnnotatedString.Builder.appendReferences(text: String, links: Links) {
    var at = 0
    for (ref in GameTexts.REFERENCE.findAll(text)) {
        append(text.substring(at, ref.range.first))
        val id = ref.groupValues[1]
        at = ref.range.last + 1
        if (!links.exists(id)) {
            append(id)
            continue
        }
        val link = LinkAnnotation.Clickable(
            tag = id,
            styles = TextLinkStyles(SpanStyle(color = RocketRed, textDecoration = TextDecoration.Underline)),
            linkInteractionListener = { links.onReference(id) },
        )
        withLink(link) { append(id) }
    }
    append(text.substring(at))
}
