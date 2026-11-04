package com.starsmuggler.ui.game

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.starsmuggler.R
import com.starsmuggler.content.LoadedContent
import com.starsmuggler.engine.Location
import com.starsmuggler.engine.ShipPosition
import com.starsmuggler.engine.content.AreaType
import com.starsmuggler.ui.Formats
import com.starsmuggler.ui.currentLocale

/** A planet by its number, or the asteroid belt (r206). */
@Composable
fun bodyText(body: Int): String =
    if (body == Location.ASTEROID_BELT) stringResource(R.string.place_asteroids) else stringResource(R.string.place_planet, Formats.integer(body, currentLocale()))

/** An area and the planet or asteroid belt it is on, such as "Puerto espacial · planeta 1". */
@Composable
fun locationText(place: Location, content: LoadedContent): String {
    val tile = content.game.tiles[place.tile]
    val area = tile?.area(place.area)
    val name = when (area?.type) {
        null -> place.area
        // The six asteroids share a type, so they go by their order on the tile.
        AreaType.ASTEROID -> stringResource(
            R.string.place_asteroid,
            Formats.integer(tile.areas.filter { it.type == AreaType.ASTEROID }.indexOf(area) + 1, currentLocale()),
        )
        else -> content.names.areaType(area.type.key)
    }
    return stringResource(R.string.place_area, name, bodyText(place.planet))
}

/** Where the starship is in its system (r212g). */
@Composable
fun shipPositionText(position: ShipPosition, content: LoadedContent): String = when (position) {
    is ShipPosition.InArea -> locationText(position.location, content)
    is ShipPosition.Orbit -> if (position.body == Location.ASTEROID_BELT) {
        stringResource(R.string.place_orbit_asteroids)
    } else {
        stringResource(R.string.place_orbit_planet, Formats.integer(position.body, currentLocale()))
    }
    is ShipPosition.Outward -> pluralStringResource(R.plurals.place_outward, position.hours, Formats.integer(position.hours, currentLocale()))
}
