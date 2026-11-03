package com.starsmuggler.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
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
fun GameText(blocks: List<TextBlock>, onReference: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (block in blocks) {
            when (block) {
                is TextBlock.Heading -> Text(
                    richText(block.text, onReference),
                    style = MaterialTheme.typography.titleSmall,
                    color = RocketRed,
                    modifier = Modifier.padding(top = 4.dp),
                )
                is TextBlock.Paragraph -> Text(richText(block.text, onReference), style = MaterialTheme.typography.bodyMedium, color = Ink)
                is TextBlock.Bullet -> Row(Modifier.padding(start = (12 * block.level).dp)) {
                    if (!block.text.first().isDigit()) {
                        Text(BULLET, style = MaterialTheme.typography.bodyMedium, color = RocketRed, modifier = Modifier.padding(end = 6.dp))
                    }
                    Text(richText(block.text, onReference), style = MaterialTheme.typography.bodyMedium, color = Ink)
                }
                is TextBlock.Table -> TextTable(block, onReference)
            }
        }
    }
}

@Composable
private fun TextTable(table: TextBlock.Table, onReference: (String) -> Unit) {
    // Wide tables, such as r235 with eleven columns, scroll sideways instead of squeezing.
    Column(
        Modifier
            .horizontalScroll(rememberScrollState())
            .border(1.dp, InkFaded),
    ) {
        table.rows.forEachIndexed { index, row ->
            Row {
                row.forEachIndexed { column, cell ->
                    Text(
                        richText(cell, onReference),
                        style = TeletypeStyles.small,
                        color = if (index == 0) RocketRed else Ink,
                        modifier = Modifier
                            .width(if (column == 0) FIRST_COLUMN else COLUMN)
                            .border(0.5.dp, InkFaded)
                            .padding(horizontal = 4.dp, vertical = 3.dp),
                    )
                }
            }
        }
    }
}

private val FIRST_COLUMN = 132.dp
private val COLUMN = 64.dp
private const val BULLET = "•"
private val BOLD = Regex("\\*\\*(.+?)\\*\\*")

/** [text] with its **bold** spans and its references made tappable. */
private fun richText(text: String, onReference: (String) -> Unit): AnnotatedString = buildAnnotatedString {
    var at = 0
    for (bold in BOLD.findAll(text)) {
        appendReferences(text.substring(at, bold.range.first), onReference)
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { appendReferences(bold.groupValues[1], onReference) }
        at = bold.range.last + 1
    }
    appendReferences(text.substring(at), onReference)
}

private fun AnnotatedString.Builder.appendReferences(text: String, onReference: (String) -> Unit) {
    var at = 0
    for (ref in GameTexts.REFERENCE.findAll(text)) {
        append(text.substring(at, ref.range.first))
        val id = ref.groupValues[1]
        val link = LinkAnnotation.Clickable(
            tag = id,
            styles = TextLinkStyles(SpanStyle(color = RocketRed, textDecoration = TextDecoration.Underline)),
            linkInteractionListener = { onReference(id) },
        )
        withLink(link) { append(id) }
        at = ref.range.last + 1
    }
    append(text.substring(at))
}
