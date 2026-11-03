package com.starsmuggler.engine

import com.starsmuggler.engine.content.GameContent
import com.starsmuggler.engine.dice.DiceHand
import com.starsmuggler.engine.dice.DiceRoll
import com.starsmuggler.engine.dice.Rng
import com.starsmuggler.engine.dice.RollRequest
import com.starsmuggler.engine.dice.RollSource

/**
 * The rules engine: it starts games and performs actions on the [content] of the game. Given the
 * same state, action and dice it always gives the same result.
 */
class Engine(private val content: GameContent) {
    /** A new game whose dice come from [seed]. */
    fun newGame(seed: Long): GameState = GameState(
        rulesVersion = RULES_VERSION,
        seed = seed,
        rng = Rng(seed),
        step = 0,
        log = listOf(LogEntry.GameStarted(step = 0, seed = seed, rulesVersion = RULES_VERSION)),
    )

    /** Why [action] cannot be performed in [state], or null if it can. Rolls no dice. */
    fun check(state: GameState, action: Action): Rejection? = when (action) {
        is Action.RollOnTable -> {
            val table = content.tables[action.table]
            when {
                table == null -> Rejection.UNKNOWN_TABLE
                table.dice == null -> Rejection.TABLE_WITHOUT_DICE
                table.row(action.key) == null -> Rejection.UNKNOWN_ROW
                else -> null
            }
        }
    }

    /**
     * Performs [action] on [state], rolling through [hand]. A rejected action, or one whose hand
     * throws (such as the player backing out of entering their dice), leaves [state] untouched.
     */
    suspend fun perform(state: GameState, action: Action, hand: DiceHand = DiceHand.APP): ActionResult {
        require(state.rulesVersion == RULES_VERSION) { "Game played with rules version ${state.rulesVersion}, engine is $RULES_VERSION" }
        check(state, action)?.let { return ActionResult.Rejected(it) }
        val turn = Turn(state, hand)
        when (action) {
            is Action.RollOnTable -> rollOnTable(turn, action)
        }
        return ActionResult.Done(turn.finish(), turn.entries)
    }

    private suspend fun rollOnTable(turn: Turn, action: Action.RollOnTable) {
        val table = content.table(action.table)
        val roll = turn.roll(RollRequest(table.dice!!, table.rule, table.id, action.modifier))
        turn.log(LogEntry.TableResult(turn.step, table.id, action.key, roll.total, table.lookup(roll.total, action.key)))
    }

    companion object {
        /** Version of the rules model, bumped when saved games stop being compatible. */
        const val RULES_VERSION = 1
    }
}

/** The work of one action: it rolls the dice and gathers the log entries, then builds the new state. */
private class Turn(private val before: GameState, private val hand: DiceHand) {
    val step = before.step + 1
    private var rng = before.rng
    val entries = mutableListOf<LogEntry>()

    suspend fun roll(request: RollRequest): DiceRoll {
        val faces = hand.physical(request)
        val roll = if (faces != null) {
            DiceRoll(request, faces, RollSource.PHYSICAL)
        } else {
            DiceRoll(request, List(request.dice.count) { rng.nextFace().also { rng = it.second }.first }, RollSource.APP)
        }
        hand.show(roll)
        log(LogEntry.Rolled(step, roll))
        return roll
    }

    fun log(entry: LogEntry) {
        entries += entry
    }

    fun finish(): GameState = before.copy(rng = rng, step = step, log = before.log + entries)
}
