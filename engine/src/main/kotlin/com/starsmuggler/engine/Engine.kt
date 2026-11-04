package com.starsmuggler.engine

import com.starsmuggler.engine.content.Air
import com.starsmuggler.engine.content.AreaType
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
    private val land = LandTravel(content)
    private val flights = Flights(content, land)

    /** The areas the starship can fly to over its planet (r203a). */
    fun flights(state: GameState): List<Flight> =
        (state.ship.position as? ShipPosition.InArea)?.let { flights.overland(it.location) } ?: emptyList()

    /** Where the boat can fly from where it is (r214b). */
    fun boatMoves(state: GameState): List<BoatMove> = flights.boatMoves(state)

    /** The stops an orbital shuttle takes Duke to (r215a). */
    fun shuttleStops(state: GameState): List<Location> = flights.shuttleStops(state)

    /** The travel lines out of Duke's area (r204g). */
    fun landRoutes(state: GameState): List<LandRoute> = land.routes(state)

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
        is Action.Travel -> land.problem(state, action.mode, action.to)
        is Action.Fly -> {
            val flight = flights(state).firstOrNull { it.to == action.to }
            when {
                state.ship.destroyed -> Rejection.GEAR_MISSING
                state.ship.hypercharges < 1 -> Rejection.NO_HYPERCHARGES
                state.ship.broken -> Rejection.GEAR_BROKEN
                !state.dukeAboard -> Rejection.NOT_ABOARD
                flight == null -> Rejection.UNREACHABLE
                !state.clock.fits(flight.tenths) -> Rejection.NOT_ENOUGH_HOURS
                else -> null
            }
        }
        Action.LaunchBoat -> when {
            state.ship.boat == null -> Rejection.GEAR_MISSING
            state.ship.boat.position != null || state.ship.position is ShipPosition.Outward -> Rejection.UNREACHABLE
            state.ship.boat.broken -> Rejection.GEAR_BROKEN
            !state.dukeAboard -> Rejection.NOT_ABOARD
            else -> null
        }
        Action.StowBoat -> when {
            state.ship.boat?.position == null -> Rejection.GEAR_MISSING
            !state.isWith(Craft.BOAT) -> Rejection.NOT_ABOARD
            state.ship.destroyed || state.ship.boat.position != state.ship.position -> Rejection.UNREACHABLE
            else -> null
        }
        is Action.MoveBoat -> when (val move = flights.boatMoves(state).firstOrNull { it.to == action.to }) {
            null -> if (state.ship.boat?.position == null) Rejection.GEAR_MISSING else Rejection.UNREACHABLE
            else -> flights.problem(state, move)
        }
        is Action.Shuttle -> when {
            action.to !in flights.shuttleStops(state) -> Rejection.UNREACHABLE
            !state.clock.fits(Clock.HOUR) -> Rejection.NOT_ENOUGH_HOURS
            state.cash < SHUTTLE_FARE -> Rejection.NOT_ENOUGH_CASH
            else -> null
        }
        Action.ContinueTrek -> when {
            state.trek == null -> Rejection.UNREACHABLE
            !state.clock.isFresh -> Rejection.NOT_ENOUGH_HOURS
            else -> null
        }
        is Action.RentVehicle -> when {
            action.capacity !in LandTravel.VEHICLE_SIZES || action.hours < 1 -> Rejection.INVALID_VALUE
            !state.clock.fits(action.hours * Clock.HOUR) -> Rejection.NOT_ENOUGH_HOURS
            rentalCost(action) > state.cash -> Rejection.NOT_ENOUGH_CASH
            else -> null
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
            is Action.Travel -> travel(turn, action.mode, land.route(state, action.to))
            is Action.Fly -> {
                val from = turn.ship.position
                turn.spend(flights(state).first { it.to == action.to }.tenths)
                turn.ship = turn.ship.copy(position = ShipPosition.InArea(action.to))
                turn.location = action.to
                use(turn, GearRef.Starship)
                turn.log(LogEntry.ShipMoved(turn.step, from, turn.ship.position))
                entryCheck(turn, action.to, STARSHIP_ENTRY)
            }
            Action.LaunchBoat -> {
                turn.ship = turn.ship.copy(boat = checkNotNull(turn.ship.boat).copy(position = turn.ship.position))
                if (turn.ship.position !is ShipPosition.InArea) turn.riding = Craft.BOAT
                turn.log(LogEntry.BoatLaunched(turn.step))
            }
            Action.StowBoat -> {
                turn.ship = turn.ship.copy(boat = checkNotNull(turn.ship.boat).copy(position = null))
                if (turn.ship.position !is ShipPosition.InArea) turn.riding = Craft.STARSHIP
                turn.log(LogEntry.BoatLaunched(turn.step, stowed = true))
            }
            is Action.MoveBoat -> moveBoat(turn, flights.boatMoves(state).first { it.to == action.to })
            is Action.Shuttle -> {
                val from = turn.location
                turn.spend(Clock.HOUR)
                turn.cash -= SHUTTLE_FARE
                turn.log(LogEntry.ShuttleTaken(turn.step, from, action.to, SHUTTLE_FARE))
                turn.location = action.to
                entryCheck(turn, action.to, SHUTTLE_ENTRY)
            }
            Action.ContinueTrek -> walk(turn, checkNotNull(state.trek))
            is Action.RentVehicle -> {
                val sees = rentalCost(action)
                turn.cash -= sees
                turn.rental = Rental(action.capacity, turn.clock.day, turn.clock.used + action.hours * Clock.HOUR)
                turn.log(LogEntry.VehicleRented(turn.step, action.capacity, action.hours, sees))
            }
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
    private suspend fun moveShip(turn: Turn, to: ShipPosition) {
        val from = turn.ship.position
        turn.spend(Sublight.MOVE)
        turn.ship = turn.ship.copy(position = to)
        turn.riding = Craft.STARSHIP
        if (to is ShipPosition.InArea) turn.location = to.location
        if (GearRef.Starship !in turn.usedToday) turn.usedToday += GearRef.Starship
        turn.log(LogEntry.ShipMoved(turn.step, from, to))
        // Landing or docking enters an area; the orbit and outward space never do (r212g).
        if (to is ShipPosition.InArea) entryCheck(turn, to.location, STARSHIP_ENTRY)
    }

    /** A boat flight: its time and fuel, and an entry check where it lands or docks (r214b, r225). */
    private suspend fun moveBoat(turn: Turn, move: BoatMove) {
        val boat = checkNotNull(turn.ship.boat)
        val from = checkNotNull(boat.position)
        turn.spend(move.tenths)
        turn.ship = turn.ship.copy(boat = boat.copy(fuel = boat.fuel - move.fuel, position = move.to))
        turn.riding = Craft.BOAT
        use(turn, GearRef.Boat)
        turn.log(LogEntry.BoatMoved(turn.step, from, move.to, move.fuel))
        if (move.to is ShipPosition.InArea) {
            turn.location = move.to.location
            entryCheck(turn, move.to.location, BOAT_ENTRY)
        }
    }

    private fun rentalCost(action: Action.RentVehicle): Int = action.capacity / LandTravel.UC_PER_SEE * action.hours

    /** Travel along one line by [mode]: an hour, an hour per step by vehicle, or a day per step on foot (r203a, r215). */
    private suspend fun travel(turn: Turn, mode: TravelMode, route: LandRoute) {
        val from = turn.location
        if (mode == TravelMode.ON_FOOT && route.walkingDays > 0) {
            return walk(turn, Trek(from, route.to, route.walkingDays))
        }
        turn.spend(if (mode == TravelMode.ON_FOOT) Clock.HOUR else route.vehicleTenths)
        if (mode == TravelMode.SKIMMER) use(turn, GearRef.DukeItem(checkNotNull(land.skimmer(turn.state()))))
        turn.log(LogEntry.Travelled(turn.step, mode, from, route.to))
        arrive(turn, mode, route.to)
    }

    /** A day of walking one land step; the last one arrives (r203a). */
    private suspend fun walk(turn: Turn, trek: Trek) {
        turn.spend(turn.clock.left)
        val left = trek.stepsLeft - 1
        turn.trek = if (left > 0) trek.copy(stepsLeft = left) else null
        turn.log(LogEntry.Travelled(turn.step, TravelMode.ON_FOOT, trek.from, trek.to, left))
        if (left == 0) arrive(turn, TravelMode.ON_FOOT, trek.to)
    }

    /**
     * Duke enters [place]: his suit gives him life support where the air is bad or missing
     * (r213), a skimmer breaks down at once in bad air (r204f), and the entry is checked for
     * detection (r225).
     */
    private suspend fun arrive(turn: Turn, mode: TravelMode, place: Location) {
        turn.location = place
        if (land.needsLifeSupport(place)) land.suit(turn.state())?.let { use(turn, GearRef.DukeItem(it)) }
        if (mode == TravelMode.SKIMMER && land.airAt(place) == Air.BAD && !land.area(place).ownAir) {
            val skimmer = GearRef.DukeItem(checkNotNull(land.skimmer(turn.state())))
            turn.broken += skimmer
            turn.log(LogEntry.BadAirBreakdown(turn.step, skimmer))
        }
        entryCheck(turn, place, mode.key)
    }

    private fun use(turn: Turn, gear: GearRef) {
        if (gear !in turn.usedToday) turn.usedToday += gear
    }

    /** Arriving from hyperspace: 1d6 on the system's entry table (r226, r207), whatever comes next. */
    private suspend fun systemEntry(turn: Turn, systemId: String) {
        val roll = turn.roll(RollRequest(D6, SYSTEM_ENTRY_RULE)).total
        turn.log(LogEntry.SystemEntry(turn.step, systemId, content.system(systemId).entryEvent(roll)))
    }

    /**
     * Entering [place] by [mode], a row of r238: the detection factor of the system's tech code
     * plus the area's adjustment, against 1d6 (r225a). Detected, an entry encounter is rolled on
     * r233 at -1 (r225b). The asteroids are entered unseen.
     */
    private suspend fun entryCheck(turn: Turn, place: Location, mode: String) {
        val area = checkNotNull(content.tile(place.tile).area(place.area)) { "No area $place" }
        if (area.type == AreaType.ASTEROID) return
        val base = content.table(DETECTION_TABLE).lookup(content.system(place.system).tech, mode)
        val factor = (base as Outcome.Value).value + content.factors(DETECTION_AREAS).factor(area.type.key)
        val roll = turn.roll(RollRequest(D6, DETECTION_RULE)).total
        val detected = roll <= factor
        turn.log(LogEntry.DetectionChecked(turn.step, place, mode, factor, roll, detected))
        if (detected) encounter(turn, area.type.key, place.system, ENTRY_ENCOUNTER_MODIFIER)
    }

    /** An encounter on r233 in the row of [areaKey]; a SIST result rolls again in the row of [systemId]. */
    private suspend fun encounter(turn: Turn, areaKey: String, systemId: String, modifier: Int) {
        val table = content.table(ENCOUNTER_TABLE)
        val total = turn.roll(RollRequest(table.dice!!, table.rule, table.id, modifier)).total
        val outcome = table.lookup(total, areaKey)
        turn.log(LogEntry.TableResult(turn.step, table.id, areaKey, total, outcome))
        if (outcome != Outcome.SystemTable) return
        val systems = content.table(SYSTEM_ENCOUNTER_TABLE)
        val again = turn.roll(RollRequest(systems.dice!!, systems.rule, systems.id)).total
        turn.log(LogEntry.TableResult(turn.step, systems.id, systemId, again, systems.lookup(again, systemId)))
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
            systemEntry(turn, option.system)
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
        private val SYSTEM_ENTRY_RULE = RuleRef("r226")
        private val DETECTION_RULE = RuleRef("r225a")
        private const val DETECTION_TABLE = "r238"
        private const val DETECTION_AREAS = "r238.area"
        private const val ENCOUNTER_TABLE = "r233"
        private const val SYSTEM_ENCOUNTER_TABLE = "r233.system"
        private const val ENTRY_ENCOUNTER_MODIFIER = -1

        /** The starship's row of the detection table (r238). */
        const val STARSHIP_ENTRY = "starship"
        private const val BOAT_ENTRY = "ships_boat"
        private const val SHUTTLE_ENTRY = "orbital_shuttle"

        /** One person, 1 UC, rides in the smallest 3 UC share of the shuttle: 1 S (r215a, e074). */
        const val SHUTTLE_FARE = 1
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
    var trek = before.trek
    var riding = before.riding
    var rental = before.rental

    /** The state as this turn has left it so far. */
    fun state(): GameState = before.copy(duke = duke, location = location, usedToday = usedToday, ship = ship, riding = riding)

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
            trek = trek,
            riding = riding,
            rental = rental,
            log = before.log + entries,
        )
        return broken.fold(state) { acc, ref -> acc.withBroken(ref, true) }
    }
}
