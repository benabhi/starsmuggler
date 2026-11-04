package com.starsmuggler.ui.map

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.starsmuggler.R
import com.starsmuggler.engine.Action
import com.starsmuggler.engine.Clock
import com.starsmuggler.engine.Craft
import com.starsmuggler.engine.GameState
import com.starsmuggler.engine.Location
import com.starsmuggler.engine.ShipPosition
import com.starsmuggler.engine.TravelMode
import com.starsmuggler.engine.content.Air
import com.starsmuggler.engine.content.Area
import com.starsmuggler.engine.content.MapPoint
import com.starsmuggler.engine.content.PlanetSide
import com.starsmuggler.engine.content.Restriction
import com.starsmuggler.engine.content.Tile
import com.starsmuggler.engine.dukeInSpace
import com.starsmuggler.ui.Formats
import com.starsmuggler.ui.components.RecordCard
import com.starsmuggler.ui.components.StampButton
import com.starsmuggler.ui.components.StampStyle
import com.starsmuggler.ui.currentLocale
import com.starsmuggler.ui.game.GameSession
import com.starsmuggler.ui.game.bodyText
import com.starsmuggler.ui.game.hoursText
import com.starsmuggler.ui.game.locationText
import com.starsmuggler.ui.game.rejectionText
import com.starsmuggler.ui.game.travelModeText
import com.starsmuggler.ui.theme.ChromeOchre
import com.starsmuggler.ui.theme.ChromeTurquoise
import com.starsmuggler.ui.theme.Ink
import com.starsmuggler.ui.theme.InkFaded
import com.starsmuggler.ui.theme.Paper
import com.starsmuggler.ui.theme.RocketRed
import com.starsmuggler.ui.theme.SpaceBlack
import com.starsmuggler.ui.theme.TeletypeStyles

/** A way to go somewhere on the planet map: over land (r203a), or flying the starship or the boat. */
private enum class Way { ON_FOOT, COMMERCIAL, SKIMMER, STARSHIP, BOAT }

/** A destination of the chosen way: the action that goes there and its time in tenths of an hour, or why not. */
private class Reach(val action: Action, val tenths: Int?, val days: Int = 0)

/**
 * The planet map (SMUG-41, PRD P4): the two retouched tiles joined into the planet (r204d), its
 * areas as touch targets with their name, type, restriction, [b]/[m] and air, Duke and his craft
 * on it, and the places a chosen way of travel reaches with their time; "Go" makes the trip.
 */
@Composable
fun PlanetMap(session: GameSession, game: GameState) {
    val content = session.content
    val position = if (game.dukeInSpace && game.riding == Craft.BOAT) game.ship.boat?.position else game.ship.position
    val here = if (game.dukeInSpace) null else game.location
    val system = content.game.system(here?.system ?: position!!.system)
    val body = here?.planet ?: when (position) {
        is ShipPosition.Orbit -> position.body
        is ShipPosition.InArea -> position.location.planet
        else -> system.planets.first().number
    }
    val halves = halves(content.game.tiles, body, system.planets.firstOrNull { it.number == body }?.tiles)
    var way by rememberSaveable { mutableStateOf(Way.ON_FOOT) }
    var selected by rememberSaveable(body, system.id) { mutableStateOf<String?>(null) }
    val reaches = reaches(session, game, way)
    val places = halves.flatMap { half -> half.tile.areas.map { Location(system.id, body, half.tile.id, it.id) } }
    val chosen = places.firstOrNull { "${it.tile}/${it.area}" == selected } // not-ui

    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            stringResource(R.string.map_planet_title, content.names.system(system.id), bodyText(body)).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = RocketRed,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (w in Way.entries) {
                StampButton(stringResource(wayText(w)), style = if (w == way) StampStyle.Solid else StampStyle.Outline, onClick = { way = w })
            }
        }
        PlanetCanvas(
            halves = halves,
            places = places,
            here = here,
            ship = (game.ship.position as? ShipPosition.InArea)?.location?.takeIf { !game.ship.destroyed },
            boat = (game.ship.boat?.position as? ShipPosition.InArea)?.location,
            reachable = reaches.filterValues { it.tenths != null || it.days > 0 }.keys,
            selected = chosen,
            onSelect = { selected = "${it.tile}/${it.area}" }, // not-ui
        )
        Text(stringResource(R.string.map_planet_legend), style = MaterialTheme.typography.bodySmall, color = InkFaded)
        chosen?.let { AreaCard(session, game, it, reaches[it]) }
    }
}

/** One tile placed on the planet: [turned] half a turn, and on the [left] or right of the seam. */
private class Half(val tile: Tile, val turned: Boolean, val left: Boolean)

/**
 * The tiles of [body] as they join (r204d): the half whose planet faces right goes left of the
 * seam. When both halves face the same way, the second turns around, as GameContent.seams does.
 */
private fun halves(tiles: Map<String, Tile>, body: Int, planetTiles: List<String>?): List<Half> {
    if (body == Location.ASTEROID_BELT || planetTiles == null) return listOf(Half(tiles.getValue(Location.ASTEROID_TILE), turned = false, left = true))
    val first = tiles.getValue(planetTiles[0])
    val second = tiles.getValue(planetTiles[1])
    val turned = first.planetSide == second.planetSide
    fun faces(tile: Tile, turn: Boolean): PlanetSide = if (turn) (if (tile.planetSide == PlanetSide.LEFT) PlanetSide.RIGHT else PlanetSide.LEFT) else tile.planetSide!!
    val a = Half(first, false, left = faces(first, false) == PlanetSide.RIGHT)
    val b = Half(second, turned, left = faces(second, turned) == PlanetSide.RIGHT)
    return listOf(a, b).sortedBy { !it.left }
}

/** The share of each scan's width left out at the planet's flat edge: the scan's margin past the seam. */
private const val SEAM_MARGIN = 0.04f

/** Where a point of [half]'s scan lands on a canvas of [size], with halves side by side. */
private fun Half.at(p: MapPoint, single: Boolean, width: Float, height: Float): Offset {
    // The original scan coordinates, turned if this half is.
    val x = if (turned) 1f - p.x else p.x
    val y = if (turned) 1f - p.y else p.y
    if (single) return Offset(x * width, y * height)
    val kept = 1f - SEAM_MARGIN
    val halfWidth = width / 2f
    return if (left) Offset(x / kept * halfWidth, y * height) else Offset(halfWidth + (x - SEAM_MARGIN) / kept * halfWidth, y * height)
}

@Composable
private fun PlanetCanvas(
    halves: List<Half>,
    places: List<Location>,
    here: Location?,
    ship: Location?,
    boat: Location?,
    reachable: Set<Location>,
    selected: Location?,
    onSelect: (Location) -> Unit,
) {
    val context = LocalContext.current
    val bitmaps = remember(halves.map { it.tile.image }) {
        halves.map { half -> context.assets.open(half.tile.image).use { BitmapFactory.decodeStream(it) }.asImageBitmap() }
    }
    val single = halves.size == 1
    val ratio = bitmaps.first().width.toFloat() / bitmaps.first().height * if (single) 1f else 2f * (1f - SEAM_MARGIN)
    val height = MAP_HEIGHT
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    // Start on the seam, the middle of the planet.
    LaunchedEffect(Unit) { scroll.scrollTo(scroll.maxValue / 2) }
    val measurer = rememberTextMeasurer()
    val marker = TeletypeStyles.small.copy(color = SpaceBlack)
    val dukeMark = stringResource(R.string.map_mark_duke)
    val shipMark = stringResource(R.string.map_mark_ship)
    val boatMark = stringResource(R.string.map_mark_boat)
    Box(
        Modifier
            .fillMaxWidth()
            .background(SpaceBlack)
            .horizontalScroll(scroll),
    ) {
        Canvas(
            Modifier
                .height(height)
                .width(height * ratio)
                .pointerInput(places) {
                    detectTapGestures { tap ->
                        val nearest = places.minByOrNull { (spot(halves, it, single, size.width.toFloat(), size.height.toFloat()) - tap).getDistance() }
                        if (nearest != null && (spot(halves, nearest, single, size.width.toFloat(), size.height.toFloat()) - tap).getDistance() < with(density) { TOUCH.toPx() }) {
                            onSelect(nearest)
                        }
                    }
                },
        ) {
            halves.forEachIndexed { i, half -> drawHalf(bitmaps[i], half, single) }
            val radius = 12.dp.toPx()
            for (place in places) {
                val center = spot(halves, place, single, size.width, size.height)
                val area = halves.first { it.tile.id == place.tile }.tile.area(place.area)!!
                if (place in reachable) drawCircle(ChromeTurquoise, radius + 7.dp.toPx(), center, style = Stroke(4.dp.toPx()))
                if (place == selected) drawCircle(ChromeOchre, radius + 12.dp.toPx(), center, style = Stroke(3.dp.toPx()))
                drawCircle(restrictionColor(area.restriction), radius, center)
                drawCircle(SpaceBlack, radius, center, style = Stroke(2.dp.toPx()))
                val marks = listOfNotNull(dukeMark.takeIf { place == here }, shipMark.takeIf { place == ship }, boatMark.takeIf { place == boat })
                if (marks.isNotEmpty()) {
                    val label = measurer.measure(marks.joinToString(" "), marker)
                    val box = Offset(center.x - label.size.width / 2f, center.y - radius - label.size.height - 4.dp.toPx())
                    drawRect(Paper, box - Offset(3.dp.toPx(), 1.dp.toPx()), androidx.compose.ui.geometry.Size(label.size.width + 6.dp.toPx(), label.size.height + 2.dp.toPx()))
                    drawText(label, topLeft = box)
                }
            }
        }
    }
}

private fun spot(halves: List<Half>, place: Location, single: Boolean, width: Float, height: Float): Offset {
    val half = halves.first { it.tile.id == place.tile }
    return half.at(half.tile.area(place.area)!!.position, single, width, height)
}

/** Draws [half]'s scan, without its seam margin and turned if need be, in its place on the canvas. */
private fun DrawScope.drawHalf(bitmap: ImageBitmap, half: Half, single: Boolean) {
    val w = bitmap.width
    val h = bitmap.height
    if (single) {
        drawImage(bitmap, dstSize = IntSize(size.width.toInt(), size.height.toInt()))
        return
    }
    val margin = (w * SEAM_MARGIN).toInt()
    // The margin lies at the planet's flat edge of the scan as drawn.
    val srcX = if (half.tile.planetSide == PlanetSide.RIGHT) 0 else margin
    val dstX = if (half.left) 0 else (size.width / 2f).toInt()
    val dst = IntSize((size.width / 2f).toInt(), size.height.toInt())
    val pivot = Offset(dstX + dst.width / 2f, dst.height / 2f)
    rotate(if (half.turned) 180f else 0f, pivot) {
        drawImage(bitmap, srcOffset = IntOffset(srcX, 0), srcSize = IntSize(w - margin, h), dstOffset = IntOffset(dstX, 0), dstSize = dst)
    }
}

/** Every area the chosen way reaches from where Duke is, with its time or the reason it cannot. */
@Composable
private fun reaches(session: GameSession, game: GameState, way: Way): Map<Location, Reach> = when (way) {
    Way.ON_FOOT, Way.COMMERCIAL, Way.SKIMMER -> {
        val mode = when (way) {
            Way.ON_FOOT -> TravelMode.ON_FOOT
            Way.COMMERCIAL -> TravelMode.COMMERCIAL
            else -> TravelMode.SKIMMER
        }
        session.landRoutes().associate { route ->
            val action = Action.Travel(mode, route.to)
            val ok = session.check(action) == null
            val days = if (mode == TravelMode.ON_FOOT) route.walkingDays else 0
            val tenths = if (mode == TravelMode.ON_FOOT) Clock.HOUR else route.vehicleTenths
            route.to to Reach(action, tenths.takeIf { ok && days == 0 }, if (ok) days else 0)
        }
    }
    // Landing or docking from space is a sublight move of an hour (r212g); over the land, a low flight (r203a).
    Way.STARSHIP -> session.sublightDestinations().mapNotNull { to ->
        val place = (to as? ShipPosition.InArea)?.location ?: return@mapNotNull null
        val action = Action.Sublight(to)
        place to Reach(action, Clock.HOUR.takeIf { session.check(action) == null })
    }.toMap() + session.flights().associate { flight ->
        val action = Action.Fly(flight.to)
        flight.to to Reach(action, flight.tenths.takeIf { session.check(action) == null })
    }
    Way.BOAT -> session.boatMoves().mapNotNull { move ->
        val to = (move.to as? ShipPosition.InArea)?.location ?: return@mapNotNull null
        val action = Action.MoveBoat(move.to)
        to to Reach(action, move.tenths.takeIf { session.check(action) == null })
    }.toMap()
}

/** The touched area: what it is, and the trip there by the chosen way when there is one. */
@Composable
private fun AreaCard(session: GameSession, game: GameState, place: Location, reach: Reach?) {
    val content = session.content
    val area = content.game.tile(place.tile).area(place.area)!!
    val planet = content.game.system(place.system).planets.firstOrNull { it.number == place.planet }
    RecordCard(locationText(place, content)) {
        Text(areaDetails(area, planet?.air), style = TeletypeStyles.small, color = Ink, modifier = Modifier.padding(vertical = 4.dp))
        when {
            reach == null -> Text(stringResource(R.string.map_area_unreachable), style = TeletypeStyles.small, color = InkFaded, modifier = Modifier.padding(vertical = 6.dp))
            reach.tenths == null && reach.days == 0 -> session.check(reach.action)?.let {
                Text(stringResource(rejectionText(it)), style = TeletypeStyles.small, color = RocketRed, modifier = Modifier.padding(vertical = 6.dp))
            }
            else -> {
                val time = if (reach.days > 0) {
                    androidx.compose.ui.res.pluralStringResource(R.plurals.travel_days, reach.days, Formats.integer(reach.days, currentLocale()))
                } else {
                    hoursText(reach.tenths!!)
                }
                StampButton(
                    stringResource(R.string.map_go, time),
                    style = StampStyle.Solid,
                    enabled = !session.busy,
                    onClick = { session.perform(reach.action) },
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun areaDetails(area: Area, air: Air?): String {
    val parts = buildList {
        add(stringResource(restrictionText(area.restriction)))
        add(
            stringResource(
                when {
                    area.ownAir -> R.string.air_own
                    air == Air.BAD -> R.string.air_bad
                    air == Air.NONE -> R.string.air_none
                    else -> R.string.air_good
                },
            ),
        )
        if (area.bank) add(stringResource(R.string.content_bank_mark))
        if (area.medical) add(stringResource(R.string.content_medical_mark))
    }
    return parts.joinToString(stringResource(R.string.list_separator))
}

private fun wayText(way: Way): Int = when (way) {
    Way.ON_FOOT -> travelModeText(TravelMode.ON_FOOT)
    Way.COMMERCIAL -> travelModeText(TravelMode.COMMERCIAL)
    Way.SKIMMER -> travelModeText(TravelMode.SKIMMER)
    Way.STARSHIP -> R.string.map_way_starship
    Way.BOAT -> R.string.bridge_boat
}

private fun restrictionText(restriction: Restriction): Int = when (restriction) {
    Restriction.WHITE -> R.string.restriction_white
    Restriction.YELLOW -> R.string.restriction_yellow
    Restriction.RED -> R.string.restriction_red
    Restriction.PURPLE -> R.string.restriction_purple
}

private fun restrictionColor(restriction: Restriction): Color = when (restriction) {
    Restriction.WHITE -> Paper
    Restriction.YELLOW -> ChromeOchre
    Restriction.RED -> RocketRed
    Restriction.PURPLE -> PURPLE
}

/** Purple areas (r204g); the theme palette has no purple of its own. */
private val PURPLE = Color(0xFF7B4FA0)

private val MAP_HEIGHT = 420.dp
private val TOUCH = 28.dp
