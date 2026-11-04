package com.starsmuggler.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.starsmuggler.R
import com.starsmuggler.engine.Action
import com.starsmuggler.engine.GameState
import com.starsmuggler.engine.JumpOption
import com.starsmuggler.engine.content.ConnectionPattern
import com.starsmuggler.ui.Formats
import com.starsmuggler.ui.components.RecordCard
import com.starsmuggler.ui.components.RecordLine
import com.starsmuggler.ui.components.StampButton
import com.starsmuggler.ui.components.StampStyle
import com.starsmuggler.ui.currentLocale
import com.starsmuggler.ui.game.GameSession
import com.starsmuggler.ui.game.rejectionText
import com.starsmuggler.ui.theme.ChromeOchre
import com.starsmuggler.ui.theme.ChromeTurquoise
import com.starsmuggler.ui.theme.Ink
import com.starsmuggler.ui.theme.InkFaded
import com.starsmuggler.ui.theme.Paper
import com.starsmuggler.ui.theme.RocketRed
import com.starsmuggler.ui.theme.SpaceBlack
import com.starsmuggler.ui.theme.TeletypeStyles
import kotlin.math.roundToInt

/**
 * The sector map (SMUG-38): the ten systems on the standard jump pattern (r234), where the ship
 * is, which systems a jump reaches with its hypercharges and risk (r212b-c, PRD P12), and where
 * Duke is wanted (r228). Touching a system shows it, and jumps there when it can.
 */
@Composable
fun SectorMap(session: GameSession, game: GameState) {
    val content = session.content
    val here = game.ship.position.system
    val options = session.jumpOptions().associateBy { it.system }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.map_sector_title).uppercase(), style = MaterialTheme.typography.labelMedium, color = RocketRed)
        SectorCanvas(
            pattern = content.game.pattern(),
            labels = content.game.pattern().nodes.associate { node ->
                val name = content.names.system(node.system)
                node.system to if (node.system in game.standing.wantedIn) stringResource(R.string.map_wanted_label, name) else name
            },
            here = here,
            reachable = options.keys,
            wanted = game.standing.wantedIn.toSet(),
            selected = selected,
            onSelect = { selected = it },
        )
        Text(stringResource(R.string.map_sector_legend), style = MaterialTheme.typography.bodySmall, color = InkFaded)
        selected?.let { SystemDetail(session, game, it, options[it]) }
    }
}

/** What the map says about [systemId], and the jump to it if there is one. */
@Composable
private fun SystemDetail(session: GameSession, game: GameState, systemId: String, option: JumpOption?) {
    val content = session.content
    val system = content.game.system(systemId)
    val locale = currentLocale()
    RecordCard(content.names.system(systemId)) {
        RecordLine(stringResource(R.string.map_codes), stringResource(R.string.map_codes_value, Formats.integer(system.wealth, locale), Formats.integer(system.tech, locale)))
        RecordLine(
            stringResource(R.string.map_bodies),
            if (system.asteroids) {
                pluralStringResource(R.plurals.map_planets_asteroids, system.planets.size, Formats.integer(system.planets.size, locale))
            } else {
                pluralStringResource(R.plurals.map_planets, system.planets.size, Formats.integer(system.planets.size, locale))
            },
        )
        RecordLine(
            stringResource(R.string.sheet_wanted),
            stringResource(if (systemId in game.standing.wantedIn) R.string.sheet_wanted_yes else R.string.sheet_wanted_no),
        )
        when {
            systemId == game.ship.position.system -> Text(stringResource(R.string.map_here), style = TeletypeStyles.small, color = Ink, modifier = Modifier.padding(vertical = 6.dp))
            option == null -> Text(stringResource(R.string.map_out_of_reach), style = TeletypeStyles.small, color = InkFaded, modifier = Modifier.padding(vertical = 6.dp))
            else -> {
                RecordLine(
                    stringResource(R.string.map_jump),
                    stringResource(
                        R.string.jump_option_cost,
                        stringResource(jumpKind(option.hops)),
                        pluralStringResource(R.plurals.jump_hypercharges, option.hypercharges, Formats.integer(option.hypercharges, locale)),
                    ),
                )
                RecordLine(
                    stringResource(R.string.map_risk),
                    if (option.risk == 0) {
                        stringResource(R.string.map_risk_none)
                    } else {
                        stringResource(
                            R.string.map_risk_value,
                            Formats.integer(option.risk, locale),
                            Formats.integer((option.risk.coerceAtMost(DIE_FACES) * 100f / DIE_FACES).roundToInt(), locale),
                        )
                    },
                )
                if (option.risk > 0) {
                    Text(stringResource(R.string.map_risk_hint), style = MaterialTheme.typography.bodySmall, color = InkFaded, modifier = Modifier.padding(vertical = 4.dp))
                }
                val jump = Action.Jump(systemId)
                val why = session.check(jump)
                why?.let { Text(stringResource(rejectionText(it)), style = TeletypeStyles.small, color = RocketRed, modifier = Modifier.padding(vertical = 4.dp)) }
                StampButton(
                    stringResource(R.string.map_jump_to, content.names.system(systemId)),
                    style = StampStyle.Solid,
                    enabled = why == null && !session.busy,
                    onClick = { session.perform(jump) },
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
    }
}

/** The name of a single, double or triple jump (r212b). */
fun jumpKind(hops: Int): Int = when (hops) {
    1 -> R.string.jump_single
    2 -> R.string.jump_double
    else -> R.string.jump_triple
}

/**
 * The pattern drawn upright on the star chart: its left-to-right axis runs top to bottom to fit
 * the portrait screen. Systems are touch targets.
 */
@Composable
private fun SectorCanvas(
    pattern: ConnectionPattern,
    /** Each system's name on the chart, marked when Duke is wanted there. */
    labels: Map<String, String>,
    here: String,
    reachable: Set<String>,
    wanted: Set<String>,
    selected: String?,
    onSelect: (String) -> Unit,
) {
    val measurer = rememberTextMeasurer()
    val labelStyle = TeletypeStyles.small.copy(color = Paper)
    val wantedStyle = TeletypeStyles.small.copy(color = RocketRed)
    Canvas(
        Modifier
            .fillMaxWidth()
            .aspectRatio(CHART_ASPECT)
            .background(SpaceBlack)
            .pointerInput(pattern) {
                detectTapGestures { tap ->
                    val layout = ChartLayout(pattern, Size(size.width.toFloat(), size.height.toFloat()))
                    val nearest = pattern.nodes.minBy { (layout.at(it.system) - tap).getDistance() }
                    if ((layout.at(nearest.system) - tap).getDistance() < TOUCH_RADIUS.toPx()) onSelect(nearest.system)
                }
            },
    ) {
        val layout = ChartLayout(pattern, size)
        val segments = pattern.routes.map { layout.at(it.a) to layout.at(it.b) }
        for ((a, b) in segments) drawLine(InkFaded, a, b, strokeWidth = 2.dp.toPx())
        val radius = 7.dp.toPx()
        val gap = 5.dp.toPx()
        val taken = pattern.nodes.map { Rect(layout.at(it.system), radius + gap) }.toMutableList()
        for (node in pattern.nodes) {
            val system = node.system
            val center = layout.at(system)
            if (system == selected) drawCircle(ChromeOchre, radius + 6.dp.toPx(), center, style = Stroke(2.dp.toPx()))
            when {
                system == here -> drawCircle(RocketRed, radius, center)
                system in reachable -> {
                    drawCircle(SpaceBlack, radius, center)
                    drawCircle(ChromeTurquoise, radius, center, style = Stroke(3.dp.toPx()))
                }
                else -> {
                    drawCircle(SpaceBlack, radius, center)
                    drawCircle(Paper, radius, center, style = Stroke(1.5.dp.toPx()))
                }
            }
            val label = measurer.measure(labels.getValue(system), if (system in wanted) wantedStyle else labelStyle)
            val w = label.size.width.toFloat()
            val h = label.size.height.toFloat()
            val reach = radius + gap
            val candidates = listOf(
                Offset(center.x + reach, center.y - h / 2),
                Offset(center.x - reach - w, center.y - h / 2),
                Offset(center.x + reach, center.y - reach - h),
                Offset(center.x + reach, center.y + reach),
                Offset(center.x - reach - w, center.y - reach - h),
                Offset(center.x - reach - w, center.y + reach),
            ).map { Rect(it, Size(w, h)) }
            val canvas = Rect(Offset.Zero, size)
            val box = candidates.firstOrNull { r ->
                canvas.contains(r.topLeft) && canvas.contains(r.bottomRight - Offset(1f, 1f)) &&
                    taken.none { it.overlaps(r) } &&
                    segments.none { (a, b) -> crosses(r, a, b) }
            } ?: candidates.first()
            taken += box
            drawText(label, topLeft = box.topLeft)
        }
    }
}

/** Where each system sits on a chart of [size]: the pattern turned upright, with margins for labels. */
private class ChartLayout(private val pattern: ConnectionPattern, private val size: Size) {
    private val xs = pattern.nodes.map { it.position.y }
    private val ys = pattern.nodes.map { it.position.x }

    fun at(system: String): Offset {
        val p = checkNotNull(pattern.position(system)) { "System $system is not on the pattern" } // not-ui
        val x = (p.y - xs.min()) / (xs.max() - xs.min())
        val y = (p.x - ys.min()) / (ys.max() - ys.min())
        return Offset(size.width * (0.28f + 0.44f * x), size.height * (0.05f + 0.9f * y))
    }
}

/** Whether the segment [a]-[b] passes through [rect], sampled finely enough for a label. */
private fun crosses(rect: Rect, a: Offset, b: Offset): Boolean =
    (0..SEGMENT_SAMPLES).any { rect.contains(a + (b - a) * (it / SEGMENT_SAMPLES.toFloat())) }

private const val SEGMENT_SAMPLES = 64

/** Width over height of the upright chart. */
private const val CHART_ASPECT = 0.8f

private val TOUCH_RADIUS = 32.dp

private const val DIE_FACES = 6
