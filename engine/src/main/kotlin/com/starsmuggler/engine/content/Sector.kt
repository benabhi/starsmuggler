package com.starsmuggler.engine.content

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The star systems of the game and how they connect (r204, r207, r234). */
@Serializable
data class Sector(
    val id: String,
    val rule: RuleRef,
    val systems: List<StarSystem>,
    /** Jump patterns between systems; the standard one is [STANDARD_PATTERN] (r234). */
    val patterns: List<ConnectionPattern>,
) {
    companion object {
        const val STANDARD_PATTERN = "standard"
    }
}

/** A star system of the sector guide (r207). Its name is a text asset keyed by [id]. */
@Serializable
data class StarSystem(
    val id: String,
    val rule: RuleRef,
    /** Wealth code, 0 to 100 (r204b). */
    val wealth: Int,
    /** Tech code, 0 to 100 (r204c). */
    val tech: Int,
    /** System entry encounter for each 1d6 result, 1 first (r207). */
    val entryEvents: List<EventRef>,
    /** Planets from the nearest to the star outwards: Planet I first (r207). */
    val planets: List<Planet>,
    val asteroids: Boolean = false,
) {
    init {
        require(wealth in 0..100) { "System $id: wealth $wealth outside 0..100" }
        require(tech in 0..100) { "System $id: tech $tech outside 0..100" }
        require(entryEvents.size == 6) { "System $id: the entry table needs 6 events, one per 1d6 result" }
        require(planets.isNotEmpty()) { "System $id has no planets" }
    }

    /** The system entry encounter for a 1d6 [roll]. */
    fun entryEvent(roll: Int): EventRef = entryEvents[roll.coerceIn(1, 6) - 1]
}

/** A planet, made of two map tiles joined in a circle (r204d). */
@Serializable
data class Planet(
    /** Its number in the system: 1 for Planet I. */
    val number: Int,
    /** The two tile ids of its layout, such as `J` and `K` for "J-K". */
    val tiles: List<String>,
    /** Gravity factor, 1 to 16 (r204e). */
    val gravity: Int,
    val air: Air,
) {
    init {
        require(tiles.size == 2) { "Planet $number: a layout has exactly two tiles" }
        require(gravity in 1..16) { "Planet $number: gravity $gravity outside 1..16" }
    }
}

/** Planet atmosphere (r204f). */
@Serializable
enum class Air {
    @SerialName("good") GOOD,
    @SerialName("bad") BAD,
    @SerialName("none") NONE,
}

/** One jump pattern between systems (r234). */
@Serializable
data class ConnectionPattern(
    val id: String,
    val rule: RuleRef,
    val routes: List<Route>,
) {
    /** The systems one jump away from [systemId]. */
    fun neighbors(systemId: String): List<String> = routes.mapNotNull { it.other(systemId) }
}

/** A two-way jump route between two systems. */
@Serializable
data class Route(val a: String, val b: String) {
    init {
        require(a != b) { "Route from $a to itself" }
    }

    fun other(systemId: String): String? = when (systemId) {
        a -> b
        b -> a
        else -> null
    }
}
