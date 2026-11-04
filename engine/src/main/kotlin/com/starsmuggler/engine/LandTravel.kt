package com.starsmuggler.engine

import com.starsmuggler.engine.content.Air
import com.starsmuggler.engine.content.Area
import com.starsmuggler.engine.content.ConnectorKind
import com.starsmuggler.engine.content.GameContent
import com.starsmuggler.engine.content.Restriction
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A way of travel over land (r203a); [key] is its row of the detection table (r238). */
@Serializable
enum class TravelMode(val key: String) {
    @SerialName("on_foot") ON_FOOT("on_foot"),
    @SerialName("commercial_vehicle") COMMERCIAL("commercial_vehicle"),
    @SerialName("skimmer") SKIMMER("skimmer"),
}

/** A travel line from Duke's area [to] a neighboring one (r204g): a triple line, or a single one of [steps] land steps. */
data class LandRoute(val to: Location, val kind: ConnectorKind, val steps: Int?) {
    /** Tenths of an hour by vehicle: one hour for a triple line, one hour per step otherwise (r203a, r215). */
    val vehicleTenths: Int get() = if (kind == ConnectorKind.TRIPLE) Clock.HOUR else (steps ?: 1) * Clock.HOUR

    /** On foot a single line takes a whole day per land step (r203a). */
    val walkingDays: Int get() = if (kind == ConnectorKind.TRIPLE) 0 else steps ?: 1
}

/** Walking a single line over several days: [stepsLeft] more days to reach [to] from [from] (r203a). */
@Serializable
data class Trek(val from: Location, val to: Location, val stepsLeft: Int)

/** A commercial vehicle of [capacity] UC rented on [day] until [until] tenths of that day (r215b, e074). */
@Serializable
data class Rental(val capacity: Int, val day: Int, val until: Int)

/** Travel over a planet's land areas on foot, by commercial vehicle or by skimmer (r203a, r204f-g, r213, r215). */
internal class LandTravel(private val content: GameContent) {
    /** Every travel line out of Duke's area, along the tile and across the planet's seam; none on the asteroids. */
    fun routes(state: GameState): List<LandRoute> {
        val here = state.location
        if (here.planet == Location.ASTEROID_BELT) return emptyList()
        val planet = content.system(here.system).planets.first { it.number == here.planet }
        val inTile = content.tile(here.tile).connectors.mapNotNull { c ->
            val other = when {
                c.to == null -> null
                c.from == here.area -> c.to
                c.to == here.area -> c.from
                else -> null
            }
            other?.let { LandRoute(here.copy(area = it), c.kind, c.steps) }
        }
        val acrossSeam = content.seams(planet).mapNotNull { s ->
            when {
                s.fromTile == here.tile && s.fromArea == here.area -> LandRoute(here.copy(tile = s.toTile, area = s.toArea), s.kind, s.steps)
                s.toTile == here.tile && s.toArea == here.area -> LandRoute(here.copy(tile = s.fromTile, area = s.fromArea), s.kind, s.steps)
                else -> null
            }
        }
        return (inTile + acrossSeam).distinctBy { it.to }
    }

    /** Why Duke cannot travel by [mode] to [to] now, or null if he can. */
    fun problem(state: GameState, mode: TravelMode, to: Location): Rejection? {
        if (state.trek != null) return Rejection.ON_TREK
        val route = routes(state).firstOrNull { it.to == to } ?: return Rejection.UNREACHABLE
        val area = area(to)
        if (!mode.mayEnter(area.restriction)) return Rejection.RESTRICTED_AREA
        if (needsLifeSupport(to) && suit(state) == null) return Rejection.NO_LIFE_SUPPORT
        return when (mode) {
            TravelMode.ON_FOOT -> when {
                route.kind == ConnectorKind.TRIPLE -> if (state.clock.fits(Clock.HOUR)) null else Rejection.NOT_ENOUGH_HOURS
                // A land step on foot is the whole day's travel.
                !state.clock.isFresh -> Rejection.NOT_ENOUGH_HOURS
                else -> null
            }
            TravelMode.COMMERCIAL -> {
                val rental = state.rental
                when {
                    rental == null || rental.day != state.clock.day || state.clock.used >= rental.until -> Rejection.NO_VEHICLE
                    !state.clock.fits(route.vehicleTenths) -> Rejection.NOT_ENOUGH_HOURS
                    state.clock.used + route.vehicleTenths > rental.until -> Rejection.RENTAL_TOO_SHORT
                    else -> null
                }
            }
            TravelMode.SKIMMER -> {
                val skimmer = skimmer(state)
                when {
                    skimmer == null -> Rejection.GEAR_MISSING
                    state.duke.items[skimmer].broken -> Rejection.GEAR_BROKEN
                    Skill.DRIVER !in state.duke.skills -> Rejection.NO_DRIVER
                    airAt(to) == Air.NONE && !area.ownAir -> Rejection.NO_AIR
                    !state.clock.fits(route.vehicleTenths) -> Rejection.NOT_ENOUGH_HOURS
                    else -> null
                }
            }
        }
    }

    fun route(state: GameState, to: Location): LandRoute = routes(state).first { it.to == to }

    fun area(place: Location): Area = checkNotNull(content.tile(place.tile).area(place.area)) { "No area $place" }

    /** The planet's air outside the domes (r204f). */
    fun airAt(place: Location): Air = content.system(place.system).planets.first { it.number == place.planet }.air

    /** Bad or no air, outside an area with its own air, needs life support (r213). */
    fun needsLifeSupport(place: Location): Boolean = airAt(place) != Air.GOOD && !area(place).ownAir

    /** The index of a working utility suit among Duke's items, which gives him life support for the day (r213). */
    fun suit(state: GameState): Int? = state.duke.items.indexOfFirst { it.id == Duke.UTILITY_SUIT && !it.broken }.takeIf { it >= 0 }

    /** The index of Duke's skimmer, broken or not. */
    fun skimmer(state: GameState): Int? = state.duke.items.indexOfFirst { it.id == SKIMMER }.takeIf { it >= 0 }

    companion object {
        const val SKIMMER = "skimmer"

        /** The commercial vehicle sizes, in UC (e074). */
        val VEHICLE_SIZES = listOf(3, 6, 9)

        /** Sees per hour for each 3 UC of capacity (e074). */
        const val UC_PER_SEE = 3
    }
}

/** Which areas a way of travel may enter, by their symbol color (r204g). */
fun TravelMode.mayEnter(restriction: Restriction): Boolean = when (restriction) {
    Restriction.WHITE -> true
    Restriction.YELLOW -> true
    Restriction.RED -> this != TravelMode.SKIMMER
    Restriction.PURPLE -> false
}
