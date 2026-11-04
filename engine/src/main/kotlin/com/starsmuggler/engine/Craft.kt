package com.starsmuggler.engine

import com.starsmuggler.engine.content.Air
import com.starsmuggler.engine.content.AreaType
import com.starsmuggler.engine.content.ConnectorKind
import com.starsmuggler.engine.content.GameContent
import com.starsmuggler.engine.content.Restriction
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.PriorityQueue

/** A craft Duke can fly in (r212, r214). */
@Serializable
enum class Craft {
    @SerialName("starship") STARSHIP,
    @SerialName("boat") BOAT,
}

/** Where a craft is, or null when the boat is stowed in the starship. */
private fun GameState.positionOf(craft: Craft): ShipPosition? = when (craft) {
    Craft.STARSHIP -> ship.position
    Craft.BOAT -> ship.boat?.position
}

/** Duke is in space, in the craft he rides, rather than in an area. */
val GameState.dukeInSpace: Boolean
    get() = riding?.let { positionOf(it) }?.let { it !is ShipPosition.InArea } ?: false

/** Whether Duke is with [craft] and may fly it: aboard it in space, or in the area where it sits. */
fun GameState.isWith(craft: Craft): Boolean {
    if (trek != null || ship.destroyed) return false
    return when (val position = positionOf(craft)) {
        null -> false
        is ShipPosition.InArea -> !dukeInSpace && position.location == location
        else -> riding == craft
    }
}

/**
 * Duke is aboard the starship: he flies it alone, so he is with it wherever it goes, and when it
 * sits in an area he must be in that same area to take off.
 */
val GameState.dukeAboard: Boolean get() = isWith(Craft.STARSHIP)

/** A place the boat can fly to, taking [tenths] of an hour and [fuel] units of fuel (r214b). */
data class BoatMove(val to: ShipPosition, val tenths: Int, val fuel: Int)

/** An area the starship can fly to over the planet, in [tenths] of an hour (r203a, r212g). */
data class Flight(val to: Location, val tenths: Int)

/**
 * Flying low over a planet at 1/10 hour per land step, the boat (r214) and the starship (r212g),
 * and the ship's boat between surface, stations and orbits. Craft may not cross yellow or red
 * areas (r204g), and the stations are reached by climbing, not along the land lines.
 */
internal class Flights(private val content: GameContent, private val land: LandTravel) {
    /** Every area reachable by air from [from] on its planet, with the tenths of the fastest way. */
    fun overland(from: Location): List<Flight> {
        if (from.planet == Location.ASTEROID_BELT || isStation(from)) return emptyList()
        val best = mutableMapOf(from to 0)
        val queue = PriorityQueue<Pair<Location, Int>>(compareBy { it.second })
        queue += from to 0
        while (queue.isNotEmpty()) {
            val (here, cost) = queue.poll()
            if (cost > best.getValue(here)) continue
            for (route in land.routesFrom(here)) {
                val area = land.area(route.to)
                if (!flies(area.restriction) || area.type == AreaType.SPACE_STATION) continue
                val next = cost + if (route.kind == ConnectorKind.TRIPLE) Clock.HOUR else (route.steps ?: 1) * TENTHS_PER_STEP
                if (next < (best[route.to] ?: Int.MAX_VALUE)) {
                    best[route.to] = next
                    queue += route.to to next
                }
            }
        }
        return best.filterKeys { it != from }.map { Flight(it.key, it.value) }.sortedBy { it.tenths }
    }

    /** Where the boat can go from where it is, with time and fuel (r214b). */
    fun boatMoves(state: GameState): List<BoatMove> {
        val from = state.ship.boat?.position ?: return emptyList()
        val system = content.system(from.system)
        return when (from) {
            is ShipPosition.Outward -> emptyList()
            is ShipPosition.InArea -> buildList {
                val here = from.location
                if (here.planet == Location.ASTEROID_BELT) {
                    add(BoatMove(ShipPosition.Orbit(system.id, here.planet), Clock.HOUR, 1))
                    return@buildList
                }
                val gravity = gravity(here)
                if (isStation(here)) {
                    add(BoatMove(ShipPosition.Orbit(system.id, here.planet), Clock.HOUR, 1))
                    for (place in areas(here.system, here.planet).filter { !isStation(it) }) {
                        add(BoatMove(ShipPosition.InArea(place), Clock.HOUR, landingFuel(place)))
                    }
                } else {
                    // Climbing to the orbit or a station burns as much fuel as the gravity (r214b).
                    add(BoatMove(ShipPosition.Orbit(system.id, here.planet), Clock.HOUR, gravity))
                    for (place in areas(here.system, here.planet).filter { isStation(it) }) add(BoatMove(ShipPosition.InArea(place), Clock.HOUR, gravity))
                    for (flight in overland(here)) add(BoatMove(ShipPosition.InArea(flight.to), flight.tenths, fuelFor(flight.tenths)))
                }
            }
            is ShipPosition.Orbit -> buildList {
                for (place in areas(system.id, from.body)) add(BoatMove(ShipPosition.InArea(place), Clock.HOUR, landingFuel(place)))
                // Another planet, or the asteroids, is a whole day and 10 fuel away (r214b).
                val bodies = system.planets.map { it.number } + if (system.asteroids) listOf(Location.ASTEROID_BELT) else emptyList()
                for (body in bodies - from.body) add(BoatMove(ShipPosition.Orbit(system.id, body), Clock.DAY, INTERPLANETARY_FUEL))
            }
        }
    }

    /** Why the boat cannot make [move] now. */
    fun problem(state: GameState, move: BoatMove): Rejection? {
        val boat = state.ship.boat
        val from = boat?.position
        return when {
            boat == null || from == null -> Rejection.GEAR_MISSING
            boat.broken -> Rejection.GEAR_BROKEN
            !state.isWith(Craft.BOAT) -> Rejection.NOT_ABOARD
            from is ShipPosition.InArea && move.to is ShipPosition.Orbit && from.location.planet != Location.ASTEROID_BELT &&
                !isStation(from.location) && move.fuel > MAX_FUEL_AT_ONCE -> Rejection.GRAVITY_TOO_HIGH
            boat.fuel < move.fuel -> Rejection.NO_FUEL
            move.tenths == Clock.DAY && !state.clock.isFresh -> Rejection.NOT_ENOUGH_HOURS
            !state.clock.fits(move.tenths) -> Rejection.NOT_ENOUGH_HOURS
            else -> null
        }
    }

    /** The spaceports and stations of Duke's planet an orbital shuttle takes him to, an hour away (r215a). */
    fun shuttleStops(state: GameState): List<Location> {
        val here = state.location
        if (state.dukeInSpace || state.trek != null || here.planet == Location.ASTEROID_BELT) return emptyList()
        val stops = areas(here.system, here.planet).filter { land.area(it).type in SHUTTLE_STOPS }
        val boarding = here in stops || (land.area(here).type == AreaType.COLONY && stops.none { land.area(it).type == AreaType.SPACEPORT })
        return if (boarding) stops - here else emptyList()
    }

    /** Every white or purple area of [body], stations included (r204g). */
    private fun areas(systemId: String, body: Int): List<Location> {
        val tiles = if (body == Location.ASTEROID_BELT) listOf(Location.ASTEROID_TILE) else content.system(systemId).planets.first { it.number == body }.tiles
        return tiles.flatMap { tile ->
            content.tile(tile).areas.filter { flies(it.restriction) }.map { Location(systemId, body, tile, it.id) }
        }
    }

    /** With air the boat glides down unpowered; without it, or on an asteroid, it burns one fuel (r204f, r214). */
    private fun landingFuel(place: Location): Int =
        if (place.planet != Location.ASTEROID_BELT && !isStation(place) && land.airAt(place) != Air.NONE) 0 else 1

    private fun gravity(place: Location): Int = content.system(place.system).planets.first { it.number == place.planet }.gravity

    private fun isStation(place: Location): Boolean = land.area(place).type == AreaType.SPACE_STATION

    private fun flies(restriction: Restriction): Boolean = restriction == Restriction.WHITE || restriction == Restriction.PURPLE

    companion object {
        /** A land step flown takes 1/10 hour (r203a). */
        const val TENTHS_PER_STEP = 1

        /** The boat burns one fuel per hour of flight, or part of one (r214b). */
        fun fuelFor(tenths: Int): Int = (tenths + Clock.HOUR - 1) / Clock.HOUR

        /** The boat's engines burn at most 9 fuel at once, so gravity 10 or more keeps it down (r214b). */
        const val MAX_FUEL_AT_ONCE = 9
        const val INTERPLANETARY_FUEL = 10

        private val SHUTTLE_STOPS = setOf(AreaType.SPACEPORT, AreaType.SPACE_STATION)
    }
}
