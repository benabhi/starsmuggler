package com.starsmuggler.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.starsmuggler.R
import com.starsmuggler.engine.Action
import com.starsmuggler.engine.GameState
import com.starsmuggler.engine.Location
import com.starsmuggler.engine.ShipPosition
import com.starsmuggler.engine.content.Air
import com.starsmuggler.engine.content.StarSystem
import com.starsmuggler.ui.Formats
import com.starsmuggler.ui.components.RecordCard
import com.starsmuggler.ui.components.StampButton
import com.starsmuggler.ui.currentLocale
import com.starsmuggler.ui.game.GameSession
import com.starsmuggler.ui.game.bodyText
import com.starsmuggler.ui.game.locationText
import com.starsmuggler.ui.game.rejectionText
import com.starsmuggler.ui.game.shipPositionText
import com.starsmuggler.ui.theme.ChromeOchre
import com.starsmuggler.ui.theme.ChromeTurquoise
import com.starsmuggler.ui.theme.InkFaded
import com.starsmuggler.ui.theme.Paper
import com.starsmuggler.ui.theme.PlanetBlue
import com.starsmuggler.ui.theme.PlanetGreen
import com.starsmuggler.ui.theme.RocketRed
import com.starsmuggler.ui.theme.SpaceBlack
import com.starsmuggler.ui.theme.TeletypeStyles

/** A place of the system chart: a planet's number, [Location.ASTEROID_BELT], or [OUTWARD] space. */
private const val OUTWARD = -1

/**
 * The system map (SMUG-39): the star, its planets from the nearest outwards, the asteroids and
 * outward space, with the starship where it is (r207, r212g). Touching a place lists the sublight
 * moves that reach it from where the ship is.
 */
@Composable
fun SystemMap(session: GameSession, game: GameState, selected: Int?, onSelect: (Int) -> Unit) {
    val content = session.content
    val system = content.game.system(game.ship.position.system)
    val position = game.ship.position
    val shipAt = placeOf(position)
    val chosen = selected ?: shipAt
    Column(
        Modifier
            .fillMaxWidth()
            .background(SpaceBlack)
            .padding(vertical = 12.dp),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Disc(ChromeOchre, 36.dp, glow = true)
            Text(
                stringResource(R.string.map_star, content.names.system(system.id)).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = ChromeOchre,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        for (place in places(system)) {
            PlaceRow(
                title = if (place == OUTWARD) stringResource(R.string.map_outward) else bodyText(place).replaceFirstChar { it.titlecase() },
                detail = placeDetail(system, place),
                color = placeColor(system, place),
                ship = if (place == shipAt) shipPositionText(position, content) else null,
                selected = place == chosen,
                asteroids = place == Location.ASTEROID_BELT,
                onClick = { onSelect(place) },
            )
        }
    }
    PlaceMoves(session, game, chosen)
}

/** The bodies of [system] from the star outwards, the asteroids after the planets, then outward space. */
private fun places(system: StarSystem): List<Int> =
    system.planets.map { it.number } + (if (system.asteroids) listOf(Location.ASTEROID_BELT) else emptyList()) + OUTWARD

private fun placeOf(position: ShipPosition): Int = when (position) {
    is ShipPosition.InArea -> position.location.planet
    is ShipPosition.Orbit -> position.body
    is ShipPosition.Outward -> OUTWARD
}

/** Whether [destination] is at [place] of the chart. */
private fun isAt(destination: ShipPosition, place: Int): Boolean = placeOf(destination) == place

@Composable
private fun placeDetail(system: StarSystem, place: Int): String {
    val locale = currentLocale()
    return when (place) {
        OUTWARD -> stringResource(R.string.map_outward_detail)
        Location.ASTEROID_BELT -> stringResource(R.string.map_asteroids_detail)
        else -> {
            val planet = system.planets.first { it.number == place }
            stringResource(
                R.string.content_planet_value,
                planet.tiles[0],
                planet.tiles[1],
                Formats.integer(planet.gravity, locale),
                stringResource(
                    when (planet.air) {
                        Air.GOOD -> R.string.air_good
                        Air.BAD -> R.string.air_bad
                        Air.NONE -> R.string.air_none
                    },
                ),
            )
        }
    }
}

/** Planets by their air: green for good, ochre for bad, grey for none (r204f). */
private fun placeColor(system: StarSystem, place: Int): Color = when (place) {
    OUTWARD -> SpaceBlack
    Location.ASTEROID_BELT -> InkFaded
    else -> when (system.planets.first { it.number == place }.air) {
        Air.GOOD -> PlanetGreen
        Air.BAD -> ChromeOchre
        Air.NONE -> PlanetBlue
    }
}

@Composable
private fun PlaceRow(
    title: String,
    detail: String,
    color: Color,
    ship: String?,
    selected: Boolean,
    asteroids: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .background(if (selected) Color.White.copy(alpha = 0.08f) else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(36.dp)) {
            val center = Offset(size.width / 2, size.height / 2)
            when {
                asteroids -> for (i in 0 until ROCKS) {
                    val angle = i * 2 * Math.PI / ROCKS
                    val r = size.minDimension * 0.36f
                    drawCircle(color, size.minDimension * 0.07f, center + Offset((r * Math.cos(angle)).toFloat(), (r * Math.sin(angle)).toFloat()))
                }
                color == SpaceBlack -> drawCircle(InkFaded, size.minDimension * 0.45f, center, style = Stroke(1.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f, 6f))))
                else -> drawCircle(color, size.minDimension * 0.4f, center)
            }
            if (ship != null) drawCircle(RocketRed, size.minDimension * 0.5f, center, style = Stroke(2.dp.toPx()))
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, color = if (selected) ChromeTurquoise else Paper)
            Text(detail, style = TeletypeStyles.small, color = InkFaded)
            ship?.let { Text(stringResource(R.string.map_ship_here, it), style = TeletypeStyles.small, color = RocketRed) }
        }
    }
}

@Composable
private fun Disc(color: Color, size: Dp, glow: Boolean = false) {
    Canvas(Modifier.size(size)) {
        val center = Offset(this.size.width / 2, this.size.height / 2)
        if (glow) drawCircle(color.copy(alpha = 0.25f), this.size.minDimension / 2, center)
        drawCircle(color, this.size.minDimension * 0.32f, center)
    }
}

/** The sublight moves that reach [place] from where the starship is, each one hour (r212g). */
@Composable
private fun PlaceMoves(session: GameSession, game: GameState, place: Int) {
    val content = session.content
    val moves = session.sublightDestinations().filter { isAt(it, place) }
    RecordCard(stringResource(R.string.map_moves_title), Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        val why = moves.firstOrNull()?.let { session.check(Action.Sublight(it)) }
        when {
            moves.isEmpty() -> Text(stringResource(R.string.map_moves_none), style = TeletypeStyles.small, color = InkFaded, modifier = Modifier.padding(vertical = 6.dp))
            why != null -> Text(stringResource(rejectionText(why)), style = TeletypeStyles.small, color = RocketRed, modifier = Modifier.padding(vertical = 6.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
            for (move in moves) {
                StampButton(
                    shipPositionText(move, content),
                    enabled = why == null && !session.busy,
                    onClick = { session.perform(Action.Sublight(move)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (game.location != (game.ship.position as? ShipPosition.InArea)?.location && game.ship.position is ShipPosition.InArea) {
            Text(stringResource(R.string.map_duke_at, locationText(game.location, content)), style = TeletypeStyles.small, color = InkFaded)
        }
    }
}

private const val ROCKS = 7
