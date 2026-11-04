package com.starsmuggler.engine

import com.starsmuggler.engine.content.AreaType
import com.starsmuggler.engine.content.GameContent
import com.starsmuggler.engine.content.Restriction
import com.starsmuggler.engine.content.StarSystem
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Where the starship is within its star system (r212g). */
@Serializable
sealed interface ShipPosition {
    val system: String

    /** Landed on an area of a planet or an asteroid, or docked at a space station (r212g). */
    @Serializable
    @SerialName("area")
    data class InArea(val location: Location) : ShipPosition {
        override val system: String get() = location.system
    }

    /** In orbit around [body]: a planet's number, or [Location.ASTEROID_BELT] (r206). */
    @Serializable
    @SerialName("orbit")
    data class Orbit(override val system: String, val body: Int) : ShipPosition

    /** [hours] sublight moves "outward", away from every planet and the asteroids (r212g). */
    @Serializable
    @SerialName("outward")
    data class Outward(override val system: String, val hours: Int) : ShipPosition {
        init {
            require(hours in 1..MAX_OUTWARD) { "Outward $hours outside 1..$MAX_OUTWARD" }
        }
    }

    companion object {
        /**
         * The farthest a ship goes outward: from four moves an undamaged ship jumps without risk
         * (r212c), so going farther gives nothing.
         */
        const val MAX_OUTWARD = 4
    }
}

/**
 * The starship's sublight moves (r206, r212f-g). Each takes one hour; moving between areas of a
 * planet's surface, at 1/10 hour per land step, needs the land graph and is not one of them.
 */
internal class Sublight(private val content: GameContent) {
    /** Why the starship cannot make a sublight move now, whatever the destination. */
    fun problem(state: GameState): Rejection? = when {
        state.ship.destroyed -> Rejection.GEAR_MISSING
        state.ship.hypercharges < 1 -> Rejection.NO_HYPERCHARGES
        state.ship.broken -> Rejection.GEAR_BROKEN
        !state.dukeAboard -> Rejection.NOT_ABOARD
        !state.clock.fits(MOVE) -> Rejection.NOT_ENOUGH_HOURS
        else -> null
    }

    /** Every place one sublight move away from where the starship is, in a stable order. */
    fun destinations(state: GameState): List<ShipPosition> {
        val from = state.ship.position
        val system = content.system(from.system)
        return when (from) {
            is ShipPosition.Outward -> buildList {
                if (from.hours < ShipPosition.MAX_OUTWARD) add(ShipPosition.Outward(system.id, from.hours + 1))
                if (from.hours > 1) add(ShipPosition.Outward(system.id, from.hours - 1)) else addAll(orbits(system))
            }
            is ShipPosition.Orbit -> buildList {
                add(ShipPosition.Outward(system.id, 1))
                addAll(orbits(system).filter { it.body != from.body })
                addAll(landings(system, from.body).map { ShipPosition.InArea(it) })
            }
            is ShipPosition.InArea -> buildList {
                val here = from.location
                add(ShipPosition.Orbit(system.id, here.planet))
                // On a planet the ship goes straight between the surface and a station; the asteroids only join through the orbit (r206).
                if (here.planet != Location.ASTEROID_BELT) {
                    val docked = isStation(here)
                    for (place in landings(system, here.planet)) {
                        if (isStation(place) != docked) add(ShipPosition.InArea(place))
                    }
                }
            }
        }
    }

    private fun orbits(system: StarSystem): List<ShipPosition.Orbit> =
        bodies(system).map { ShipPosition.Orbit(system.id, it) }

    /** The planets' numbers, then the asteroid belt if the system has one. */
    private fun bodies(system: StarSystem): List<Int> =
        system.planets.map { it.number } + if (system.asteroids) listOf(Location.ASTEROID_BELT) else emptyList()

    /** The areas of [body] a starship may enter: white and purple ones (r204g). */
    private fun landings(system: StarSystem, body: Int): List<Location> {
        val tiles = if (body == Location.ASTEROID_BELT) {
            listOf(Location.ASTEROID_TILE)
        } else {
            system.planets.first { it.number == body }.tiles
        }
        return tiles.flatMap { tileId ->
            content.tile(tileId).areas
                .filter { it.restriction == Restriction.WHITE || it.restriction == Restriction.PURPLE }
                .map { Location(system.id, body, tileId, it.id) }
        }
    }

    private fun isStation(place: Location): Boolean = content.tile(place.tile).area(place.area)?.type == AreaType.SPACE_STATION

    companion object {
        /** Each sublight move takes one hour (r212g). */
        const val MOVE = Clock.HOUR
    }
}

/**
 * Duke is aboard the starship: he flies it alone, so he is with it wherever it goes, and when it
 * sits in an area he must be in that same area to take off.
 */
val GameState.dukeAboard: Boolean
    get() = !ship.destroyed && trek == null && when (val position = ship.position) {
        is ShipPosition.InArea -> position.location == location
        else -> true
    }
