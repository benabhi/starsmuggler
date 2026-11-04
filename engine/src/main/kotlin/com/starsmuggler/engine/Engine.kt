package com.starsmuggler.engine

import com.starsmuggler.engine.content.Dice
import com.starsmuggler.engine.content.EventRef
import com.starsmuggler.engine.content.GameContent
import com.starsmuggler.engine.content.Outcome
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
    private val sublight = Sublight(content)
    private val hyperspace = Hyperspace(content)

    /** Every system the starship can jump to from its own, with the cost and risk of each jump (r212b-c). */
    fun jumpOptions(state: GameState): List<JumpOption> = hyperspace.options(state)

    /** Every place the starship can reach with one sublight move from where it is (r212g), whether or not it can move now. */
    fun sublightDestinations(state: GameState): List<ShipPosition> = sublight.destinations(state)

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
    fun check(state: GameState, action: Action): Rejection? = if (state.ending != null) Rejection.GAME_OVER else checkPlaying(state, action)

    private fun checkPlaying(state: GameState, action: Action): Rejection? = when (action) {
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
        is Action.EndGame -> null
        is Action.AstuciaRoll -> null
        is Action.PayPrincipal -> when {
            state.loan.isPaidOff -> Rejection.LOAN_PAID_OFF
            action.sees <= 0 || action.sees > state.loan.principal -> Rejection.INVALID_AMOUNT
            action.sees > state.cash -> Rejection.NOT_ENOUGH_CASH
            else -> null
        }
        is Action.UseGear -> when (state.gear(action.gear)?.broken) {
            null -> Rejection.GEAR_MISSING
            true -> Rejection.GEAR_BROKEN
            false -> null
        }
        is Action.EditSheet -> action.edit.problem(state, isItem = { it in content.equipment }, isSystem = { it in content.systems })
        is Action.PreventiveMaintenance -> if (state.maintenance?.day == state.clock.day) Rejection.ALREADY_MAINTAINED else null
        is Action.AdjustCash -> when {
            action.sees == 0 -> Rejection.INVALID_AMOUNT
            state.cash + action.sees < 0 -> Rejection.NOT_ENOUGH_CASH
            else -> null
        }
        is Action.Jump -> hyperspace.problem(state) ?: when (val option = hyperspace.options(state).firstOrNull { it.system == action.to }) {
            null -> Rejection.UNREACHABLE
            else -> if (state.ship.hypercharges < option.hypercharges) Rejection.NO_HYPERCHARGES else null
        }
        is Action.Sublight -> sublight.problem(state) ?: if (action.to in sublight.destinations(state)) null else Rejection.UNREACHABLE
    }

    /**
     * Performs [action] on [state], rolling through [hand]. A rejected action, or one whose hand
     * throws (such as the player backing out of entering their dice), leaves [state] untouched.
     */
    suspend fun perform(state: GameState, action: Action, hand: DiceHand = DiceHand.APP): ActionResult {
        require(state.rulesVersion == RULES_VERSION) { "Game played with rules version ${state.rulesVersion}, engine is $RULES_VERSION" }
        check(state, action)?.let { return ActionResult.Rejected(it) }
        // A hand edit changes the sheet before the turn starts, so its fields carry it into the new state.
        val turn = Turn(if (action is Action.EditSheet) state.edited(action.edit) else state, hand)
        when (action) {
            is Action.RollOnTable -> rollOnTable(turn, action)
            is Action.PassTime -> turn.spend(action.tenths)
            Action.PassRestOfDay -> turn.spend(turn.clock.left)
            Action.EndDay -> endDay(turn)
            is Action.EndGame -> {
                turn.ending = action.ending
                val ended = state.copy(ending = action.ending)
                turn.log(LogEntry.GameEnded(turn.step, action.ending, ended.won, turn.clock.day, turn.cash, turn.loan.principal))
            }
            is Action.AstuciaRoll -> astuciaRoll(turn, state.duke.astucia, action.modifier)
            is Action.PayPrincipal -> payPrincipal(turn, action.sees)
            is Action.UseGear -> {
                if (action.gear !in turn.usedToday) turn.usedToday += action.gear
                turn.log(LogEntry.GearUsed(turn.step, action.gear))
            }
            is Action.EditSheet -> turn.log(LogEntry.SheetEdited(turn.step, action.edit))
            is Action.PreventiveMaintenance -> {
                turn.maintenance = Maintenance(turn.clock.day, action.by)
                turn.log(LogEntry.MaintenanceDone(turn.step, action.by))
            }
            is Action.AdjustCash -> {
                turn.cash += action.sees
                turn.log(LogEntry.CashAdjusted(turn.step, action.sees, turn.cash))
            }
            is Action.Sublight -> moveShip(turn, action.to)
            is Action.Jump -> jump(turn, hyperspace.options(state).first { it.system == action.to })
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

    /** Closes the day: breakdown checks (r203d), the weekly interest (r203e), and the next morning. */
    private suspend fun endDay(turn: Turn) {
        val clock = turn.clock
        breakdownChecks(turn)
        turn.log(LogEntry.DayEnded(turn.step, clock.day, clock.overtime))
        if (clock.endsWeek) {
            turn.log(LogEntry.WeekEnded(turn.step, clock.week))
            weeklyInterest(turn)
        }
        turn.clock = clock.nextDay()
        turn.usedToday = emptyList()
        val deadline = turn.loan.deadline
        if (turn.loan.isDefaulted && !turn.loan.repossession && deadline != null && turn.clock.day > deadline) {
            turn.loan = turn.loan.copy(repossession = true)
            turn.log(LogEntry.RepossessionStarted(turn.step, turn.clock.day))
        }
    }

    /**
     * Each piece of gear used today rolls 2d6 plus the maintenance bonus and breaks down when the
     * roll is not higher than its tech level (r218a). Gear that cannot break, such as anything of
     * tech 1, is not rolled.
     */
    private suspend fun breakdownChecks(turn: Turn) {
        val bonus = turn.maintenance?.bonus(turn.clock.day) ?: 0
        for (ref in turn.usedToday) {
            val gear = turn.gear(ref) ?: continue
            if (gear.broken || gear.tech < BREAKDOWN_DICE.min + bonus) continue
            val total = turn.roll(RollRequest(BREAKDOWN_DICE, BREAKDOWN_RULE, modifier = bonus)).total
            val broke = total <= gear.tech
            if (broke) turn.broken += ref
            turn.log(LogEntry.BreakdownChecked(turn.step, ref, gear.tech, total, broke))
        }
    }

    /** The interest due at the end of each week, paid from cash or the loan defaults (r203e). */
    private fun weeklyInterest(turn: Turn) {
        val loan = turn.loan
        if (loan.isPaidOff || loan.isDefaulted) return
        val week = turn.clock.week
        if (turn.cash >= Loan.WEEKLY_INTEREST) {
            turn.cash -= Loan.WEEKLY_INTEREST
            turn.log(LogEntry.InterestPaid(turn.step, week, Loan.WEEKLY_INTEREST))
        } else {
            turn.loan = loan.copy(defaultedOn = turn.clock.day)
            turn.log(LogEntry.LoanDefaulted(turn.step, week, loan.principal, checkNotNull(turn.loan.deadline)))
        }
    }

    /** One hour of sublight flight; Duke flies with the ship, and the ship was used today (r203d, r212g). */
    private fun moveShip(turn: Turn, to: ShipPosition) {
        val from = turn.ship.position
        turn.spend(Sublight.MOVE)
        turn.ship = turn.ship.copy(position = to)
        if (to is ShipPosition.InArea) turn.location = to.location
        if (GearRef.Starship !in turn.usedToday) turn.usedToday += GearRef.Starship
        turn.log(LogEntry.ShipMoved(turn.step, from, to))
    }

    /** A hyperspace jump: the hypercharges go, and the risk roll brings the ship out at 4 moves outward or dissipates it (r212b-d). */
    private suspend fun jump(turn: Turn, option: JumpOption) {
        val from = turn.ship.position.system
        turn.ship = turn.ship.copy(hypercharges = turn.ship.hypercharges - option.hypercharges)
        if (GearRef.Starship !in turn.usedToday) turn.usedToday += GearRef.Starship
        val roll = if (option.risk > 0) turn.roll(RollRequest(D6, JUMP_RISK_RULE)).total else null
        val succeeded = roll == null || roll > option.risk
        turn.log(LogEntry.Jumped(turn.step, from, option.system, option.hops, option.hypercharges, option.risk, roll, succeeded))
        if (succeeded) {
            turn.ship = turn.ship.copy(position = ShipPosition.Outward(option.system, ShipPosition.MAX_OUTWARD))
        } else {
            dissipate(turn, option.system)
        }
    }

    /**
     * The ship and everything aboard are lost (r212d). The pilot's cabin stasis unit triggers on
     * its own, so with one aboard Duke's fate is rolled on r240; without it he dies (r212e).
     */
    private suspend fun dissipate(turn: Turn, system: String) {
        val inStasis = turn.ship.stasis >= STASIS_FOR_DUKE
        turn.log(LogEntry.Dissipated(turn.step, system, inStasis))
        if (!inStasis) return die(turn)
        val table = content.table(STASIS_TABLE)
        val total = turn.roll(RollRequest(table.dice!!, table.rule, table.id)).total
        val outcome = table.lookup(total)
        turn.log(LogEntry.TableResult(turn.step, table.id, null, total, outcome))
        when ((outcome as? Outcome.Key)?.key) {
            "stasis_lost", "stasis_culture_shock" -> return die(turn)
            "stasis_1200_years", "stasis_230_years" -> turn.cash = 0
            "stasis_3_years" -> turn.cash /= 2
        }
        val place = hyperspace.recoveryPlace(system)
        turn.ship = turn.ship.copy(destroyed = true, position = ShipPosition.InArea(place))
        turn.location = place
        turn.duke = turn.duke.copy(items = emptyList())
        turn.log(LogEntry.StasisRecovered(turn.step, place, turn.cash))
    }

    /** Duke died in the middle of an action: the game ends lost (r203f). */
    private fun die(turn: Turn) {
        turn.ending = Ending.DIED
        turn.log(LogEntry.GameEnded(turn.step, Ending.DIED, won = false, turn.clock.day, turn.cash, turn.loan.principal))
    }

    private fun payPrincipal(turn: Turn, sees: Int) {
        turn.cash -= sees
        val principal = turn.loan.principal - sees
        turn.loan = if (principal == 0) Loan(principal = 0) else turn.loan.copy(principal = principal)
        turn.log(LogEntry.PrincipalPaid(turn.step, sees, principal))
        if (principal == 0) turn.log(LogEntry.LoanPaidOff(turn.step))
    }

    companion object {
        /** Version of the rules model, bumped when saved games stop being compatible. */
        const val RULES_VERSION = 1

        private val D6 = Dice(1)
        private val BREAKDOWN_DICE = Dice(2)
        private val BREAKDOWN_RULE = RuleRef("r218a")
        private val ASTUCIA_RULE = RuleRef("r202")
        private val MONEY_RULE = RuleRef("r232a")
        private val START_EVENT = EventRef("e001")
        private val JUMP_RISK_RULE = RuleRef("r212c")
        private const val STASIS_TABLE = "r240"

        /** Capacity units Duke takes inside a stasis unit (r211a). */
        private const val STASIS_FOR_DUKE = 1
        private const val STARTING_CASH_PER_PIP = 100
        private const val STARTING_CASH_BASE = 150
    }
}

/** The work of one action: it rolls the dice and gathers the log entries, then builds the new state. */
private class Turn(private val before: GameState, private val hand: DiceHand, val step: Int = before.step + 1) {
    private var rng = before.rng
    var clock = before.clock
    var cash = before.cash
    var loan = before.loan
    var usedToday = before.usedToday
    var maintenance = before.maintenance
    var ending = before.ending
    var ship = before.ship
    var duke = before.duke
    var location = before.location

    /** Gear that broke down during this action. */
    val broken = mutableListOf<GearRef>()

    fun gear(ref: GearRef): GearStatus? = before.gear(ref)?.let { if (ref in broken) it.copy(broken = true) else it }
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

    fun finish(): GameState {
        val state = before.copy(
            rng = rng,
            step = step,
            clock = clock,
            cash = cash,
            loan = loan,
            usedToday = usedToday,
            maintenance = maintenance,
            ending = ending,
            ship = ship,
            duke = duke,
            location = location,
            log = before.log + entries,
        )
        return broken.fold(state) { acc, ref -> acc.withBroken(ref, true) }
    }
}
