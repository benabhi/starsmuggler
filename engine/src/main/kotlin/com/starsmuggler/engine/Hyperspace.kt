package com.starsmuggler.engine

import com.starsmuggler.engine.content.AreaType
import com.starsmuggler.engine.content.GameContent
import com.starsmuggler.engine.content.Sector

/** A jump the starship can make from its system: [hops] systems along the routes (r212b). */
data class JumpOption(
    val system: String,
    /** 1 for a single jump, 2 for a double one, 3 for a triple one. */
    val hops: Int,
    /** Hypercharges it uses. */
    val hypercharges: Int,
    /** A 1d6 roll not higher than this makes the ship dissipate; 0 is a sure jump (r212c). */
    val risk: Int,
)

/** Hyperspace jumps between systems (r212a-e), along the standard connection pattern (r234). */
internal class Hyperspace(private val content: GameContent) {
    /** Why the starship cannot jump now, wherever to. */
    fun problem(state: GameState): Rejection? = when {
        state.ship.destroyed -> Rejection.GEAR_MISSING
        state.ship.broken -> Rejection.GEAR_BROKEN
        !state.dukeAboard -> Rejection.NOT_ABOARD
        state.ship.hypercharges < HYPERCHARGES.first() -> Rejection.NO_HYPERCHARGES
        else -> null
    }

    /** Every system within a triple jump of the starship's, nearest first, whether or not there are hypercharges for it. */
    fun options(state: GameState): List<JumpOption> {
        val risk = risk(state)
        return hops(state.ship.position.system).map { (system, hops) -> JumpOption(system, hops, HYPERCHARGES[hops - 1], risk) }
    }

    /**
     * The risk factor of a jump from where the starship is (r212c): +5 from a planet's surface,
     * +4 from the orbit, an asteroid or a station, then +3, +2 and +1 from one, two and three
     * moves outward. Damage to the ship would add one per hit; the ship has no damage yet (r217).
     */
    fun risk(state: GameState): Int = when (val position = state.ship.position) {
        is ShipPosition.InArea -> {
            val area = content.tile(position.location.tile).area(position.location.area)
            if (position.location.planet == Location.ASTEROID_BELT || area?.type == AreaType.SPACE_STATION) 4 else 5
        }
        is ShipPosition.Orbit -> 4
        is ShipPosition.Outward -> ShipPosition.MAX_OUTWARD - position.hours
    }

    /** Systems one to three jumps away from [from] along the routes, with their distance. */
    private fun hops(from: String): List<Pair<String, Int>> {
        val pattern = content.pattern(Sector.STANDARD_PATTERN)
        val distance = linkedMapOf(from to 0)
        var frontier = listOf(from)
        for (hop in 1..HYPERCHARGES.size) {
            frontier = frontier.flatMap { pattern.neighbors(it) }.distinct().filter { it !in distance }
            for (system in frontier) distance[system] = hop
        }
        return distance.filter { it.value > 0 }.map { it.key to it.value }
    }

    /**
     * Where Duke wakes up after a stasis recovery (r240): the first spaceport, scientific area or
     * space station of [systemId], from Planet I outwards and then the asteroids.
     */
    fun recoveryPlace(systemId: String): Location {
        val system = content.system(systemId)
        val bodies = system.planets.map { it.number to it.tiles } +
            if (system.asteroids) listOf(Location.ASTEROID_BELT to listOf(Location.ASTEROID_TILE)) else emptyList()
        for ((body, tiles) in bodies) {
            for (tile in tiles) {
                val area = content.tile(tile).areas.firstOrNull { it.type in RECOVERY_AREAS } ?: continue
                return Location(systemId, body, tile, area.id)
            }
        }
        throw IllegalStateException("System $systemId has no spaceport, scientific area or station")
    }

    companion object {
        /** Hypercharges of a single, double and triple jump (r212b). */
        val HYPERCHARGES = listOf(1, 3, 5)

        private val RECOVERY_AREAS = setOf(AreaType.SPACEPORT, AreaType.SCIENTIFIC, AreaType.SPACE_STATION)
    }
}
