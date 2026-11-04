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
}

/** What performing an action gave. */
sealed interface ActionResult {
    /** The action happened: the new [state] and the log [entries] it added. */
    data class Done(val state: GameState, val entries: List<LogEntry>) : ActionResult

    /** The action was not possible; the state stays as it was. */
    data class Rejected(val reason: Rejection) : ActionResult
}
