package com.starsmuggler.engine

import com.starsmuggler.engine.content.Dice
import com.starsmuggler.engine.content.EventRef
import com.starsmuggler.engine.content.GameContent
import com.starsmuggler.engine.content.RuleRef
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
    /**
     * A new game whose dice come from [seed] (e001): Duke with his [astucia] (r202), his ship and
     * gear, and 1d6 x 100 + 150 Sees in his pocket, at Regari's spaceport on day 1. Its rolls go
     * through [hand] and are logged at step 0.
     */
    suspend fun newGame(seed: Long, astucia: AstuciaSetup = AstuciaSetup.Roll, hand: DiceHand = DiceHand.APP): GameState {
        val blank = GameState(
            rulesVersion = RULES_VERSION,
            seed = seed,
            rng = Rng(seed),
            step = 0,
            duke = Duke(astucia = Astucia.MIN),
            cash = 0,
            log = listOf(LogEntry.GameStarted(step = 0, seed = seed, rulesVersion = RULES_VERSION)),
        )
        val turn = Turn(blank, hand, step = 0)
        val value = when (astucia) {
            AstuciaSetup.Roll -> turn.roll(RollRequest(D6, ASTUCIA_RULE)).total
            is AstuciaSetup.Adjust -> Astucia.adjusted(astucia.previous, astucia.adjustment)
        }
        val adjust = astucia as? AstuciaSetup.Adjust
        turn.log(LogEntry.AstuciaSet(0, value, adjust?.previous, adjust?.adjustment ?: 0))
        val cash = turn.roll(RollRequest(D6, MONEY_RULE, event = START_EVENT)).total * STARTING_CASH_PER_PIP + STARTING_CASH_BASE
        turn.log(LogEntry.StartingCash(0, cash))
        return turn.finish().copy(duke = Duke(astucia = value), cash = cash)
    }

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
        is Action.PassTime -> when {
            action.tenths <= 0 -> Rejection.NO_TIME
            !action.event && !state.clock.fits(action.tenths) -> Rejection.NOT_ENOUGH_HOURS
            else -> null
        }
        Action.PassRestOfDay -> if (state.clock.left == 0) Rejection.NO_TIME else null
        Action.EndDay -> null
        is Action.AstuciaRoll -> null
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
            is Action.PassTime -> turn.spend(action.tenths)
            Action.PassRestOfDay -> turn.spend(turn.clock.left)
            Action.EndDay -> endDay(turn)
            is Action.AstuciaRoll -> astuciaRoll(turn, state.duke.astucia, action.modifier)
        }
        return ActionResult.Done(turn.finish(), turn.entries)
    }

    private suspend fun rollOnTable(turn: Turn, action: Action.RollOnTable) {
        val table = content.table(action.table)
        val roll = turn.roll(RollRequest(table.dice!!, table.rule, table.id, action.modifier))
        turn.log(LogEntry.TableResult(turn.step, table.id, action.key, roll.total, table.lookup(roll.total, action.key)))
    }

    private suspend fun astuciaRoll(turn: Turn, astucia: Int, modifier: Int) {
        val total = turn.roll(RollRequest(D6, ASTUCIA_RULE, modifier = modifier)).total
        turn.log(LogEntry.AstuciaRolled(turn.step, astucia, total, success = total <= astucia))
    }

    /** Closes the day and opens the next one. The end-of-day breakdowns (r203d) and the weekly loan payment (r203e) hook in here. */
    private fun endDay(turn: Turn) {
        val clock = turn.clock
        turn.log(LogEntry.DayEnded(turn.step, clock.day, clock.overtime))
        if (clock.endsWeek) turn.log(LogEntry.WeekEnded(turn.step, clock.week))
        turn.clock = clock.nextDay()
    }

    companion object {
        /** Version of the rules model, bumped when saved games stop being compatible. */
        const val RULES_VERSION = 1

        private val D6 = Dice(1)
        private val ASTUCIA_RULE = RuleRef("r202")
        private val MONEY_RULE = RuleRef("r232a")
        private val START_EVENT = EventRef("e001")
        private const val STARTING_CASH_PER_PIP = 100
        private const val STARTING_CASH_BASE = 150
    }
}

/** The work of one action: it rolls the dice and gathers the log entries, then builds the new state. */
private class Turn(private val before: GameState, private val hand: DiceHand, val step: Int = before.step + 1) {
    private var rng = before.rng
    var clock = before.clock
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

    /** Lets [tenths] of an hour go by, past the day's ten hours if need be (r203). */
    fun spend(tenths: Int) {
        val overtimeBefore = clock.overtime
        clock = clock.spend(tenths)
        log(LogEntry.TimePassed(step, tenths, clock.overtime - overtimeBefore))
    }

    fun log(entry: LogEntry) {
        entries += entry
    }

    fun finish(): GameState = before.copy(rng = rng, step = step, clock = clock, log = before.log + entries)
}
