package com.starsmuggler.engine

/** Something the player does. The engine checks it and turns it into a new [GameState]. */
sealed interface Action {
    /** Roll on a table of the game, in its row [key] (null for a one-column table). */
    data class RollOnTable(val table: String, val key: String? = null, val modifier: Int = 0) : Action
}

/** Why an action cannot be performed now; the interface shows it next to the greyed-out action. */
enum class Rejection {
    UNKNOWN_TABLE,

    /** The table is looked up by another number, not rolled (such as r238 by tech code). */
    TABLE_WITHOUT_DICE,
    UNKNOWN_ROW,
}

/** What performing an action gave. */
sealed interface ActionResult {
    /** The action happened: the new [state] and the log [entries] it added. */
    data class Done(val state: GameState, val entries: List<LogEntry>) : ActionResult

    /** The action was not possible; the state stays as it was. */
    data class Rejected(val reason: Rejection) : ActionResult
}
