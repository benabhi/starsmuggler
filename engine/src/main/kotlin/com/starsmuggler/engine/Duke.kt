package com.starsmuggler.engine

import kotlinx.serialization.Serializable

/** A skill a character may have (r201). */
@Serializable
enum class Skill { PILOT_NAVIGATOR, STARSHIP_GUNNER }

/** An item of the equipment table (r236) owned at tech level [tech] (r210); [broken] until repaired (r218). */
@Serializable
data class Item(val id: String, val tech: Int, val broken: Boolean = false)

/** Duke Springer, the player's character (r201b). */
@Serializable
data class Duke(
    val marksmanship: Int = 5,
    val melee: Int = 6,
    val endurance: Int = 10,
    val skills: List<Skill> = listOf(Skill.PILOT_NAVIGATOR, Skill.STARSHIP_GUNNER),
    /** Astucia, 0 to 6 (r202); no other character has it. */
    val astucia: Int,
    /** Personal equipment (e001). */
    val items: List<Item> = listOf(Item(UTILITY_SUIT, 1), Item(SMALL_ARMS, 1)),
) {
    init {
        require(astucia in Astucia.MIN..Astucia.MAX) { "Astucia $astucia outside ${Astucia.MIN}..${Astucia.MAX}" }
    }

    companion object {
        const val UTILITY_SUIT = "utility_suit"
        const val SMALL_ARMS = "small_arms"
    }
}

/** The Antelope-class starship (r212), as it starts the game (e001). */
@Serializable
data class Ship(
    val tech: Int = 1,
    /** Hypercharges for jumps (r212b). */
    val hypercharges: Int = 6,
    /** Tech level of the starship guns (r216a), null without them. */
    val guns: Int? = 1,
    /** Capacity units of the stasis unit in the pilot's cabin (r212e). */
    val stasis: Int = 2,
    val boat: Boat? = Boat(),
    /** Broken down until repaired (r218). */
    val broken: Boolean = false,
    val gunsBroken: Boolean = false,
    /** Where it is in its system (r212g): landed at Regari's spaceport to start (e001). */
    val position: ShipPosition = ShipPosition.InArea(Location.START),
)

/** The Hopper-class ship's boat (r214). */
@Serializable
data class Boat(
    val tech: Int = 1,
    /** Fuel units, FUEL (r211). */
    val fuel: Int = 15,
    val broken: Boolean = false,
)

/** Where the player is: an area of a tile of a planet, or of the asteroid belt, of a system (r204, r206). */
@Serializable
data class Location(val system: String, val planet: Int, val tile: String, val area: String) {
    companion object {
        /** The [planet] number of the asteroid belt, whose areas are on [ASTEROID_TILE] (r206). */
        const val ASTEROID_BELT = 0
        const val ASTEROID_TILE = "asteroids"

        /** The spaceport of Regari's only planet (e001). */
        val START = Location(system = "regari", planet = 1, tile = "K", area = "spaceport")
    }
}

/** Legal standing (r228): papers in order and the systems where the player is wanted. */
@Serializable
data class Standing(val papersInOrder: Boolean = true, val wantedIn: List<String> = emptyList())

/** Astucia (r202) between games. */
object Astucia {
    const val MIN = 0
    const val MAX = 6

    /**
     * The adjustment the rules suggest after a game: +1 after a loss (the game was too hard),
     * -1 after a win (more challenge). The player accepts it, inverts it or leaves it out.
     */
    fun suggestion(won: Boolean): Int = if (won) -1 else 1

    /** The new Astucia from the [previous] one and an [adjustment] of -1, 0 or +1, kept within 0 to 6. */
    fun adjusted(previous: Int, adjustment: Int): Int {
        require(adjustment in -1..1) { "Astucia changes by one at most, got $adjustment" }
        return (previous + adjustment).coerceIn(MIN, MAX)
    }
}

/** How Astucia is set at the start of a game (r202). */
@Serializable
sealed interface AstuciaSetup {
    /** First game: roll 1d6. */
    @Serializable
    data object Roll : AstuciaSetup

    /** A later game: the [previous] Astucia changed by [adjustment] (-1, 0 or +1). */
    @Serializable
    data class Adjust(val previous: Int, val adjustment: Int) : AstuciaSetup {
        init {
            require(previous in Astucia.MIN..Astucia.MAX) { "Previous Astucia $previous outside ${Astucia.MIN}..${Astucia.MAX}" }
            require(adjustment in -1..1) { "Astucia changes by one at most, got $adjustment" }
        }
    }
}
