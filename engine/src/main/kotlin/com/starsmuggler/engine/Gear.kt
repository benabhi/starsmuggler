package com.starsmuggler.engine

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A piece of equipment of the game state that can be used and can break down (r218). */
@Serializable
sealed interface GearRef {
    @Serializable
    @SerialName("starship")
    data object Starship : GearRef

    @Serializable
    @SerialName("starship_guns")
    data object StarshipGuns : GearRef

    @Serializable
    @SerialName("boat")
    data object Boat : GearRef

    /** Duke's personal item at [index] of [Duke.items]. */
    @Serializable
    @SerialName("item")
    data class DukeItem(val index: Int) : GearRef
}

/** Its tech level and whether it is broken down, or null if the game has no such gear. */
data class GearStatus(val tech: Int, val broken: Boolean)

fun GameState.gear(ref: GearRef): GearStatus? = when (ref) {
    // The guns and the boat go with a destroyed ship.
    GearRef.Starship, GearRef.StarshipGuns, GearRef.Boat -> if (ship.destroyed) null else shipGear(ref)
    is GearRef.DukeItem -> duke.items.getOrNull(ref.index)?.let { GearStatus(it.tech, it.broken) }
}

private fun GameState.shipGear(ref: GearRef): GearStatus? = when (ref) {
    GearRef.Starship -> GearStatus(ship.tech, ship.broken)
    GearRef.StarshipGuns -> ship.guns?.let { GearStatus(it, ship.gunsBroken) }
    GearRef.Boat -> ship.boat?.let { GearStatus(it.tech, it.broken) }
    is GearRef.DukeItem -> null
}

/** Every piece of gear the game has, in a stable order. */
fun GameState.allGear(): List<GearRef> =
    listOf(GearRef.Starship, GearRef.StarshipGuns, GearRef.Boat).filter { gear(it) != null } +
        duke.items.indices.map { GearRef.DukeItem(it) }

/** The state with [ref] broken down or repaired. */
internal fun GameState.withBroken(ref: GearRef, broken: Boolean): GameState = when (ref) {
    GearRef.Starship -> copy(ship = ship.copy(broken = broken))
    GearRef.StarshipGuns -> copy(ship = ship.copy(gunsBroken = broken))
    GearRef.Boat -> copy(ship = ship.copy(boat = checkNotNull(ship.boat).copy(broken = broken)))
    is GearRef.DukeItem -> copy(duke = duke.copy(items = duke.items.mapIndexed { i, item -> if (i == ref.index) item.copy(broken = broken) else item }))
}

/** Who did preventive maintenance (r218b). */
@Serializable
enum class Maintainer { ENGINEER, ROBOT_MG }

/** Preventive maintenance done on [day] by [by] (r218b). */
@Serializable
data class Maintenance(val day: Int, val by: Maintainer) {
    /** What it adds to breakdown rolls on [today]: an engineer +2 that day and +1 the two next, a robot MG +1 that day and the next. */
    fun bonus(today: Int): Int = when (by) {
        Maintainer.ENGINEER -> when (today - day) {
            0 -> 2
            1, 2 -> 1
            else -> 0
        }
        Maintainer.ROBOT_MG -> if (today - day in 0..1) 1 else 0
    }
}
