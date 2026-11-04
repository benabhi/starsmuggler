package com.starsmuggler.engine

import com.starsmuggler.engine.EngineTest.Companion.runSync
import com.starsmuggler.engine.EngineTest.Companion.start
import com.starsmuggler.engine.SublightTest.Companion.REGARI
import com.starsmuggler.engine.content.EventRef
import com.starsmuggler.engine.content.Outcome
import com.starsmuggler.engine.dice.DiceHand
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class EntryTest {
    private val engine = Engine(SublightTest.content())
    private val orbit = ShipPosition.Orbit(REGARI, 1)
    private val belt = ShipPosition.Orbit(REGARI, Location.ASTEROID_BELT)
    private val colony = Location(REGARI, 1, "J", "colony")
    private val station = Location(REGARI, 1, "J", "space_station")

    @Test
    fun `a roll above the detection factor enters unseen`() {
        val done = done(at(orbit), Action.Sublight(ShipPosition.InArea(colony)), faces(5))
        // Starship at tech 30: factor 4 (r238).
        assertEquals(LogEntry.DetectionChecked(done.state.step, colony, Engine.STARSHIP_ENTRY, 4, 5, detected = false), done.entries.last())
    }

    @Test
    fun `detected, the entry encounter rolls r233 at -1`() {
        val done = done(at(orbit), Action.Sublight(ShipPosition.InArea(colony)), faces(4, 1))
        assertTrue(assertIs<LogEntry.DetectionChecked>(done.entries.first { it is LogEntry.DetectionChecked }).detected)
        assertEquals(LogEntry.TableResult(done.state.step, "r233", "colony", 0, Outcome.Event(EventRef("e011"))), done.entries.last())
    }

    @Test
    fun `a SIST result rolls again on the system's row`() {
        val done = done(at(orbit), Action.Sublight(ShipPosition.InArea(colony)), faces(1, 5, 3))
        assertEquals(LogEntry.TableResult(done.state.step, "r233.system", REGARI, 3, Outcome.Event(EventRef("e122"))), done.entries.last())
    }

    @Test
    fun `a station adds two to the factor`() {
        val done = done(at(orbit), Action.Sublight(ShipPosition.InArea(station)), faces(6, 1))
        assertEquals(6, assertIs<LogEntry.DetectionChecked>(done.entries.first { it is LogEntry.DetectionChecked }).factor)
    }

    @Test
    fun `the orbit and the asteroids are entered unseen`() {
        val none = DiceHand.physical { error("No detection roll expected") }
        done(engine.start(1), Action.Sublight(orbit), none)
        val asteroid = ShipPosition.InArea(Location(REGARI, Location.ASTEROID_BELT, Location.ASTEROID_TILE, "asteroid_1"))
        done(at(belt), Action.Sublight(asteroid), none)
    }

    private fun at(position: ShipPosition): GameState {
        val game = engine.start(1)
        return game.copy(ship = game.ship.copy(position = position))
    }

    private fun faces(vararg values: Int): DiceHand {
        val queue = ArrayDeque(values.toList())
        return DiceHand.physical { request -> List(request.dice.count) { queue.removeFirst() } }
    }

    private fun done(state: GameState, action: Action, hand: DiceHand): ActionResult.Done = assertIs(runSync { engine.perform(state, action, hand) })
}
