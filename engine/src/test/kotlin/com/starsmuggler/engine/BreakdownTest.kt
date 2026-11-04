package com.starsmuggler.engine

import com.starsmuggler.engine.EngineTest.Companion.content
import com.starsmuggler.engine.EngineTest.Companion.runSync
import com.starsmuggler.engine.EngineTest.Companion.start
import com.starsmuggler.engine.dice.DiceHand
import com.starsmuggler.engine.dice.RollRequest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BreakdownTest {
    private val engine = Engine(content())
    private val sidearm = GearRef.DukeItem(1)

    @Test
    fun `the game lists its gear, all of tech 1 and working`() {
        val game = engine.start(1)
        assertEquals(listOf(GearRef.Starship, GearRef.StarshipGuns, GearRef.Boat, GearRef.DukeItem(0), GearRef.DukeItem(1)), game.allGear())
        assertTrue(game.allGear().all { game.gear(it) == GearStatus(1, false) })
        assertEquals(null, game.gear(GearRef.DukeItem(5)))
    }

    @Test
    fun `gear of tech 1 never breaks and is not rolled`() {
        val used = play(engine.start(1), Action.UseGear(sidearm))
        val done = done(used, Action.EndDay, DiceHand.physical { error("No roll expected") })
        assertTrue(done.entries.none { it is LogEntry.BreakdownChecked })
        assertEquals(emptyList(), done.state.usedToday)
    }

    @Test
    fun `used gear breaks down when 2d6 is not higher than its tech`() {
        val used = play(withTech(sidearm, 6), Action.UseGear(sidearm))
        val broke = done(used, Action.EndDay, faces(3, 3))
        assertEquals(LogEntry.BreakdownChecked(broke.state.step, sidearm, 6, 6, broke = true), broke.entries.first { it is LogEntry.BreakdownChecked })
        assertTrue(broke.state.gear(sidearm)!!.broken)
        val held = done(used, Action.EndDay, faces(3, 4))
        assertFalse(held.state.gear(sidearm)!!.broken)
        assertEquals(false, (held.entries.first { it is LogEntry.BreakdownChecked } as LogEntry.BreakdownChecked).broke)
    }

    @Test
    fun `only gear used today is checked`() {
        val game = withTech(sidearm, 12)
        val done = done(game, Action.EndDay, DiceHand.physical { error("Nothing was used") })
        assertTrue(done.entries.none { it is LogEntry.BreakdownChecked })
    }

    @Test
    fun `broken gear cannot be used`() {
        val broken = done(play(withTech(sidearm, 12), Action.UseGear(sidearm)), Action.EndDay).state
        assertTrue(broken.gear(sidearm)!!.broken, "2d6 is always at most 12")
        assertEquals(Rejection.GEAR_BROKEN, engine.check(broken, Action.UseGear(sidearm)))
        assertEquals(Rejection.GEAR_MISSING, engine.check(broken, Action.UseGear(GearRef.DukeItem(9))))
        val noBoat = broken.copy(ship = broken.ship.copy(boat = null))
        assertEquals(Rejection.GEAR_MISSING, engine.check(noBoat, Action.UseGear(GearRef.Boat)))
    }

    @Test
    fun `using gear twice checks it once`() {
        val used = play(play(withTech(GearRef.Starship, 6), Action.UseGear(GearRef.Starship)), Action.UseGear(GearRef.Starship))
        assertEquals(listOf<GearRef>(GearRef.Starship), used.usedToday)
        val asked = mutableListOf<RollRequest>()
        done(used, Action.EndDay, DiceHand.physical { asked += it; listOf(6, 6) })
        assertEquals(1, asked.size)
        assertEquals("r218a", asked.single().rule.id)
    }

    @Test
    fun `an engineer adds 2 that day and 1 the next two`() {
        val m = Maintenance(day = 5, by = Maintainer.ENGINEER)
        assertEquals(listOf(0, 2, 1, 1, 0), (4..8).map { m.bonus(it) })
        val robot = Maintenance(day = 5, by = Maintainer.ROBOT_MG)
        assertEquals(listOf(0, 1, 1, 0), (4..7).map { robot.bonus(it) })
    }

    @Test
    fun `maintenance is added to the breakdown roll`() {
        val maintained = play(play(withTech(sidearm, 6), Action.PreventiveMaintenance(Maintainer.ENGINEER)), Action.UseGear(sidearm))
        val asked = mutableListOf<RollRequest>()
        val done = done(maintained, Action.EndDay, DiceHand.physical { asked += it; listOf(2, 3) })
        assertEquals(2, asked.single().modifier)
        assertFalse(done.state.gear(sidearm)!!.broken, "5 + 2 is higher than tech 6")
        assertEquals(Rejection.ALREADY_MAINTAINED, engine.check(maintained, Action.PreventiveMaintenance(Maintainer.ROBOT_MG)))
    }

    @Test
    fun `maintenance can make a roll impossible to fail`() {
        val maintained = play(play(withTech(sidearm, 3), Action.PreventiveMaintenance(Maintainer.ENGINEER)), Action.UseGear(sidearm))
        val done = done(maintained, Action.EndDay, DiceHand.physical { error("2 + 2 is already higher than tech 3") })
        assertTrue(done.entries.none { it is LogEntry.BreakdownChecked })
    }

    @Test
    fun `gear, use and maintenance are saved with the game`() {
        val game = play(play(withTech(GearRef.Boat, 4), Action.UseGear(GearRef.Boat)), Action.PreventiveMaintenance(Maintainer.ROBOT_MG))
        assertEquals(game, Json.decodeFromString<GameState>(Json.encodeToString(game)))
    }

    /** A new game where [ref] is of tech [tech], as the bench or a bought item would have it. */
    private fun withTech(ref: GearRef, tech: Int): GameState {
        val game = engine.start(1)
        return when (ref) {
            GearRef.Starship -> game.copy(ship = game.ship.copy(tech = tech))
            GearRef.StarshipGuns -> game.copy(ship = game.ship.copy(guns = tech))
            GearRef.Boat -> game.copy(ship = game.ship.copy(boat = game.ship.boat!!.copy(tech = tech)))
            is GearRef.DukeItem -> game.copy(duke = game.duke.copy(items = game.duke.items.mapIndexed { i, item -> if (i == ref.index) item.copy(tech = tech) else item }))
        }
    }

    private fun faces(vararg values: Int) = DiceHand.physical { values.toList() }

    private fun play(state: GameState, action: Action): GameState = done(state, action).state

    private fun done(state: GameState, action: Action, hand: DiceHand = DiceHand.APP): ActionResult.Done =
        assertIs<ActionResult.Done>(runSync { engine.perform(state, action, hand) })
}
