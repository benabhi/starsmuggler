package com.starsmuggler.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.unit.dp
import com.starsmuggler.ui.theme.Ink
import com.starsmuggler.ui.theme.InkFaded
import com.starsmuggler.ui.theme.Paper
import com.starsmuggler.ui.theme.PaperShade
import com.starsmuggler.ui.theme.RocketRed
import com.starsmuggler.ui.theme.TeletypeStyles

/**
 * A retro index card, the base of the record sheet tabs (PRD section 7, item 9): a red
 * heading band and ruled lines below it.
 */
@Composable
fun RecordCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(4.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(PaperShade)
            .border(1.5.dp, Ink, shape),
    ) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.titleMedium,
            color = Paper,
            modifier = Modifier
                .fillMaxWidth()
                .background(RocketRed)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
        Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), content = content)
    }
}

/** One ruled line of a [RecordCard]: a label and its typed-in value. */
@Composable
fun RecordLine(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .drawBehind {
                val y = size.height - 1.dp.toPx()
                drawLine(
                    InkFaded,
                    Offset(0f, y),
                    Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 3.dp.toPx())),
                )
            }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Ink, modifier = Modifier.weight(1f))
        Text(value, style = TeletypeStyles.large, color = Ink)
    }
}
