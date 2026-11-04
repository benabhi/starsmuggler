package com.starsmuggler.engine

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A characteristic on Duke's record (r201a, r202). */
@Serializable
enum class Stat(val range: IntRange) {
    MARKSMANSHIP(0..6),
    MELEE(0..6),
    ENDURANCE(0..20),
    ASTUCIA(Astucia.MIN..Astucia.MAX),
}

/**
 * A change written on the record sheet by hand, for an event the engine does not resolve yet
 * (PRD P2). It is an action like any other: checked, logged and saved, never undone.
 */
@Serializable
sealed interface SheetEdit {
    @Serializable
    @SerialName("stat")
    data class SetStat(val stat: Stat, val value: Int) : SheetEdit

    @Serializable
    @SerialName("tech")
    data class SetTech(val gear: GearRef, val tech: Int) : SheetEdit

    /** Broken down, or repaired by hand (r218, r219). */
    @Serializable
    @SerialName("broken")
    data class SetBroken(val gear: GearRef, val broken: Boolean) : SheetEdit

    /** A personal item of the equipment table (r236) gained, such as from an event. */
    @Serializable
    @SerialName("add_item")
    data class AddItem(val id: String, val tech: Int) : SheetEdit

    /** Duke's item at [index] lost, sold or given away. */
    @Serializable
    @SerialName("remove_item")
    data class RemoveItem(val index: Int) : SheetEdit

    @Serializable
    @SerialName("hypercharges")
    data class SetHypercharges(val value: Int) : SheetEdit

    @Serializable
    @SerialName("boat_fuel")
    data class SetBoatFuel(val value: Int) : SheetEdit

    @Serializable
    @SerialName("papers")
    data class SetPapers(val inOrder: Boolean) : SheetEdit

    /** Wanted, or no longer wanted, in [system] (r228). */
    @Serializable
    @SerialName("wanted")
    data class SetWanted(val system: String, val wanted: Boolean) : SheetEdit

    companion object {
        val TECH = 1..6
        val COUNT = 0..999
    }
}

/** Why [edit] cannot be written on the sheet of [state], or null if it can; [isItem] and [isSystem] check ids against the content. */
internal fun SheetEdit.problem(state: GameState, isItem: (String) -> Boolean, isSystem: (String) -> Boolean): Rejection? = when (this) {
    is SheetEdit.SetStat -> invalidUnless(value in stat.range)
    is SheetEdit.SetTech -> if (state.gear(gear) == null) Rejection.GEAR_MISSING else invalidUnless(tech in SheetEdit.TECH)
    is SheetEdit.SetBroken -> if (state.gear(gear) == null) Rejection.GEAR_MISSING else null
    is SheetEdit.AddItem -> if (!isItem(id)) Rejection.UNKNOWN_ID else invalidUnless(tech in SheetEdit.TECH)
    is SheetEdit.RemoveItem -> if (index !in state.duke.items.indices) Rejection.GEAR_MISSING else null
    is SheetEdit.SetHypercharges -> invalidUnless(value in SheetEdit.COUNT)
    is SheetEdit.SetBoatFuel -> if (state.ship.boat == null) Rejection.GEAR_MISSING else invalidUnless(value in SheetEdit.COUNT)
    is SheetEdit.SetPapers -> null
    is SheetEdit.SetWanted -> if (!isSystem(system)) Rejection.UNKNOWN_ID else null
}

private fun invalidUnless(ok: Boolean): Rejection? = if (ok) null else Rejection.INVALID_VALUE

/** The state with [edit] written on its sheet; [problem] must have passed. */
internal fun GameState.edited(edit: SheetEdit): GameState = when (edit) {
    is SheetEdit.SetStat -> copy(
        duke = when (edit.stat) {
            Stat.MARKSMANSHIP -> duke.copy(marksmanship = edit.value)
            Stat.MELEE -> duke.copy(melee = edit.value)
            Stat.ENDURANCE -> duke.copy(endurance = edit.value)
            Stat.ASTUCIA -> duke.copy(astucia = edit.value)
        },
    )
    is SheetEdit.SetTech -> when (val ref = edit.gear) {
        GearRef.Starship -> copy(ship = ship.copy(tech = edit.tech))
        GearRef.StarshipGuns -> copy(ship = ship.copy(guns = edit.tech))
        GearRef.Boat -> copy(ship = ship.copy(boat = checkNotNull(ship.boat).copy(tech = edit.tech)))
        is GearRef.DukeItem -> copy(duke = duke.copy(items = duke.items.mapIndexed { i, item -> if (i == ref.index) item.copy(tech = edit.tech) else item }))
    }
    is SheetEdit.SetBroken -> withBroken(edit.gear, edit.broken)
    is SheetEdit.AddItem -> copy(duke = duke.copy(items = duke.items + Item(edit.id, edit.tech)))
    is SheetEdit.RemoveItem -> copy(
        duke = duke.copy(items = duke.items.filterIndexed { i, _ -> i != edit.index }),
        // Items after the removed one move up a place.
        usedToday = usedToday.mapNotNull { ref ->
            when {
                ref !is GearRef.DukeItem -> ref
                ref.index == edit.index -> null
                ref.index > edit.index -> GearRef.DukeItem(ref.index - 1)
                else -> ref
            }
        },
    )
    is SheetEdit.SetHypercharges -> copy(ship = ship.copy(hypercharges = edit.value))
    is SheetEdit.SetBoatFuel -> copy(ship = ship.copy(boat = checkNotNull(ship.boat).copy(fuel = edit.value)))
    is SheetEdit.SetPapers -> copy(standing = standing.copy(papersInOrder = edit.inOrder))
    is SheetEdit.SetWanted -> copy(
        standing = standing.copy(
            wantedIn = if (edit.wanted) (standing.wantedIn + edit.system).distinct() else standing.wantedIn - edit.system,
        ),
    )
}
