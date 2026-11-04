package com.starsmuggler.engine

/** Something the player does. The engine checks it and turns it into a new [GameState]. */
sealed interface Action {
    /** Roll on a table of the game, in its row [key] (null for a one-column table). */
    data class RollOnTable(val table: String, val key: String? = null, val modifier: Int = 0) : Action

    /**
     * Let [tenths] of an hour go by, such as for an event resolved by hand (PRD P2). An [event]
     * that runs past the day's ten hours takes overtime the same day (r203); anything else must
     * fit in the hours left.
     */
    data class PassTime(val tenths: Int, val event: Boolean = true) : Action

    /** An event takes the rest of the day (r203). */
    data object PassRestOfDay : Action

    /** An Astucia roll asked for by an event: 1d6 plus [modifier], a success when not higher than Astucia (r202). */
    data class AstuciaRoll(val modifier: Int = 0) : Action

    /** Pay [sees] of the loan's principal from cash (r203e). */
    data class PayPrincipal(val sees: Int) : Action

    /** Add (or, if negative, take) [sees] of cash, for an event resolved by hand (PRD P2). */
    data class AdjustCash(val sees: Int) : Action

    /** Mark [gear] as used today, so it is checked for breakdowns at the end of the day (r203d). */
    data class UseGear(val gear: GearRef) : Action

    /** A day of preventive maintenance by [by] (r218b). */
    data class PreventiveMaintenance(val by: Maintainer) : Action

    /** A change written on the record sheet by hand (PRD P2). */
    data class EditSheet(val edit: SheetEdit) : Action

    /** End the game (r203f): retire, or record Duke's death. */
    data class EndGame(val ending: Ending) : Action

    /** Move the starship one hour with its sublight drive to [to] (r212g). */
    data class Sublight(val to: ShipPosition) : Action

    /** Jump through hyperspace to the system [to], up to three systems along the routes (r212a-d). */
    data class Jump(val to: String) : Action

    /** Travel by [mode] to [to], a land area joined to Duke's by a travel line (r203a). */
    data class Travel(val mode: TravelMode, val to: Location) : Action

    /** Walk one more land step of a trek: the whole day (r203a). */
    data object ContinueTrek : Action

    /** Rent a commercial vehicle of [capacity] UC for [hours] hours, from a commercial transport event (e074, r215b). */
    data class RentVehicle(val capacity: Int, val hours: Int) : Action

    /** Close the day: end-of-day checks, end of the week every ten days, and the next morning (r203). */
    data object EndDay : Action
}

/** Why an action cannot be performed now; the interface shows it next to the greyed-out action. */
enum class Rejection {
    UNKNOWN_TABLE,

    /** The table is looked up by another number, not rolled (such as r238 by tech code). */
    TABLE_WITHOUT_DICE,
    UNKNOWN_ROW,

    /** Not enough hours left today to start it (r203a). */
    NOT_ENOUGH_HOURS,

    /** No time to pass. */
    NO_TIME,

    /** Not that much cash in Duke's pocket. */
    NOT_ENOUGH_CASH,

    /** An amount of money that makes no sense here, such as zero or more than is owed. */
    INVALID_AMOUNT,

    /** The loan is already paid off. */
    LOAN_PAID_OFF,

    /** The game has no such gear, such as a boat after losing it. */
    GEAR_MISSING,

    /** Broken down: it cannot be used until repaired (r218). */
    GEAR_BROKEN,

    /** Preventive maintenance was already done today. */
    ALREADY_MAINTAINED,

    /** A value outside what the rules allow, such as Astucia 7. */
    INVALID_VALUE,

    /** An equipment or system id the game does not have. */
    UNKNOWN_ID,

    /** No hypercharge aboard: the sublight drive needs at least one (r212f). */
    NO_HYPERCHARGES,

    /** Duke is not where the starship is, so he cannot fly it. */
    NOT_ABOARD,

    /** The starship cannot get there in one sublight move (r212g). */
    UNREACHABLE,

    /** Duke is walking a long line and can only go on (r203a). */
    ON_TREK,

    /** That way of travel may not enter the area: its symbol color forbids it (r204g). */
    RESTRICTED_AREA,

    /** Bad or no air there, and no working utility suit (r213). */
    NO_LIFE_SUPPORT,

    /** No commercial vehicle rented for now (r215b). */
    NO_VEHICLE,

    /** The rented time ends before the trip would (r215b). */
    RENTAL_TOO_SHORT,

    /** A skimmer needs a qualified driver (r215c). */
    NO_DRIVER,

    /** A skimmer's engine breathes air: it cannot go where there is none (r204f). */
    NO_AIR,

    /** The game has ended: nothing more can be done in it. */
    GAME_OVER,
}

/** What performing an action gave. */
sealed interface ActionResult {
    /** The action happened: the new [state] and the log [entries] it added. */
    data class Done(val state: GameState, val entries: List<LogEntry>) : ActionResult

    /** The action was not possible; the state stays as it was. */
    data class Rejected(val reason: Rejection) : ActionResult
}
