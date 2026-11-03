package com.starsmuggler.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.starsmuggler.ui.theme.ChromeOchre
import com.starsmuggler.ui.theme.Ink
import com.starsmuggler.ui.theme.InkFaded
import com.starsmuggler.ui.theme.PaperShade
import com.starsmuggler.ui.theme.RocketRed
import com.starsmuggler.ui.theme.SpaceBlack
import com.starsmuggler.ui.theme.TeletypeStyles
import kotlin.math.cos
import kotlin.math.sin

/** Activity hours in a day (r203). */
const val HOURS_PER_DAY = 10

// The dial sweeps 270 degrees, open at the bottom like a fifties gauge.
private const val SWEEP = 270f
private const val START = 135f

/**
 * Instrument-panel dial of the day's 10 activity hours (r203): the red arc is the time
 * already spent and the ochre needle points at the current hour.
 */
@Composable
fun HourDial(
    hoursUsed: Int,
    description: String,
    modifier: Modifier = Modifier,
    size: Dp = 160.dp,
    showNumbers: Boolean = true,
) {
    val measurer = rememberTextMeasurer()
    val used = hoursUsed.coerceIn(0, HOURS_PER_DAY)
    Canvas(modifier.size(size).semantics { contentDescription = description }) {
        val ring = this.size.minDimension * 0.08f
        val radius = this.size.minDimension / 2 - ring
        val center = Offset(this.size.width / 2, this.size.height / 2)
        val arcTopLeft = Offset(center.x - radius, center.y - radius)
        val arcSize = Size(radius * 2, radius * 2)

        drawCircle(PaperShade, radius + ring, center)
        drawCircle(Ink, radius + ring, center, style = Stroke(width = 2.dp.toPx()))
        drawArc(InkFaded, START, SWEEP, false, arcTopLeft, arcSize, alpha = 0.35f, style = Stroke(ring, cap = StrokeCap.Butt))
        drawArc(RocketRed, START, SWEEP * used / HOURS_PER_DAY, false, arcTopLeft, arcSize, style = Stroke(ring, cap = StrokeCap.Butt))

        for (hour in 0..HOURS_PER_DAY) {
            val angle = Math.toRadians((START + SWEEP * hour / HOURS_PER_DAY).toDouble())
            val outer = radius - ring * 0.6f
            val inner = outer - if (hour % 5 == 0) ring * 0.9f else ring * 0.5f
            drawLine(
                Ink,
                Offset(center.x + outer * cos(angle).toFloat(), center.y + outer * sin(angle).toFloat()),
                Offset(center.x + inner * cos(angle).toFloat(), center.y + inner * sin(angle).toFloat()),
                strokeWidth = 1.5.dp.toPx(),
            )
            if (!showNumbers) continue
            val label = measurer.measure(hour.toString(), TeletypeStyles.small)
            val textRadius = radius - ring * 2.3f
            drawText(
                label,
                color = Ink,
                topLeft = Offset(
                    center.x + textRadius * cos(angle).toFloat() - label.size.width / 2,
                    center.y + textRadius * sin(angle).toFloat() - label.size.height / 2,
                ),
            )
        }

        val needle = Math.toRadians((START + SWEEP * used / HOURS_PER_DAY).toDouble())
        // Short enough to pass under the hour numbers.
        val tip = radius * if (showNumbers) 0.48f else 0.7f
        drawLine(
            ChromeOchre,
            center,
            Offset(center.x + tip * cos(needle).toFloat(), center.y + tip * sin(needle).toFloat()),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawCircle(SpaceBlack, ring * 0.7f, center)
        drawCircle(ChromeOchre, ring * 0.35f, center)
    }
}
