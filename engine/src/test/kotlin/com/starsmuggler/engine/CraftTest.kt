package com.starsmuggler.engine

import com.starsmuggler.engine.EngineTest.Companion.runSync
import com.starsmuggler.engine.EngineTest.Companion.start
import com.starsmuggler.engine.LandTravelTest.Companion.SYSTEM
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CraftTest {
    private val engine = Engine(LandTravelTest.content())

    private val port = Location(SYSTEM, 1, "L", "port")
    private val colony = Location(SYSTEM, 1, "R", "colony")
    private val base = Location(SYSTEM, 1, "R", "base")
    private val orbit = ShipPosition.Orbit(SYSTEM, 1)

    @Test
    fun `the starship flies over white and purple areas at a tenth of an hour per step`() {
        val game = landed(engine)
        // The city is yellow and the ruins red: the ship goes round them, and the station is reached by climbing.
        assertEquals(listOf(Flight(colony, 3)), engine.flights(game))
        val done = done(engine, game, Action.Fly(colony))
        assertEquals(ShipPosition.InArea(colony), done.state.ship.position)
        assertEquals(colony, done.state.location)
        assertEquals(3, done.state.clock.used)
        assertTrue(done.entries.any { it is LogEntry.DetectionChecked && it.mode == Engine.STARSHIP_ENTRY })
    }

    @Test
    fun `the boat climbs at the cost of the gravity and flies over the land for a fuel an hour`() {
        val launched = done(engine, landed(engine), Action.LaunchBoat).state
        assertEquals(ShipPosition.InArea(port), launched.ship.boat!!.position)
        assertEquals(
            listOf(
                BoatMove(orbit, Clock.HOUR, 7),
                BoatMove(ShipPosition.InArea(base), Clock.HOUR, 7),
                BoatMove(ShipPosition.InArea(colony), 3, 1),
            ),
            engine.boatMoves(launched),
        )
        val flown = done(engine, launched, Action.MoveBoat(ShipPosition.InArea(colony))).state
        assertEquals(14, flown.ship.boat!!.fuel)
        assertEquals(colony, flown.location)
        assertTrue(flown.isWith(Craft.BOAT))
        assertFalse(flown.dukeAboard)
        assertEquals(flown, Json.decodeFromString<GameState>(Json.encodeToString(flown)))
    }

    @Test
    fun `in orbit the boat is Duke's ride, and it glides down where there is air`() {
        val launched = done(engine, landed(engine), Action.LaunchBoat).state
        val up = done(engine, launched, Action.MoveBoat(orbit)).state
        assertEquals(15 - 7, up.ship.boat!!.fuel)
        assertTrue(up.dukeInSpace)
        assertEquals(Rejection.IN_SPACE, engine.check(up, Action.Travel(TravelMode.ON_FOOT, port)))
        assertEquals(BoatMove(ShipPosition.InArea(port), Clock.HOUR, 0), engine.boatMoves(up).first { it.to == ShipPosition.InArea(port) })
        assertEquals(BoatMove(ShipPosition.InArea(base), Clock.HOUR, 1), engine.boatMoves(up).first { it.to == ShipPosition.InArea(base) })
    }

    @Test
    fun `gravity 10 or more keeps the boat down`() {
        val heavy = Engine(LandTravelTest.content(gravity = 12))
        val launched = done(heavy, landed(heavy), Action.LaunchBoat).state
        assertEquals(Rejection.GRAVITY_TOO_HIGH, heavy.check(launched, Action.MoveBoat(orbit)))
        val empty = launched.copy(ship = launched.ship.copy(boat = launched.ship.boat!!.copy(fuel = 0)))
        assertEquals(Rejection.NO_FUEL, heavy.check(empty, Action.MoveBoat(ShipPosition.InArea(colony))))
    }

    @Test
    fun `the boat leaves and rejoins the starship in orbit`() {
        val game = landed(engine).let { it.copy(ship = it.ship.copy(position = orbit)) }
        val out = done(engine, game, Action.LaunchBoat).state
        assertEquals(Craft.BOAT, out.riding)
        assertFalse(out.dukeAboard)
        assertEquals(Rejection.UNREACHABLE, engine.check(out, Action.LaunchBoat))
        val back = done(engine, out, Action.StowBoat).state
        assertEquals(null, back.ship.boat!!.position)
        assertEquals(Craft.STARSHIP, back.riding)
        assertTrue(back.dukeAboard)
    }

    @Test
    fun `the orbital shuttle runs between the planet's spaceports and stations`() {
        val game = landed(engine)
        assertEquals(listOf(base), engine.shuttleStops(game))
        val done = done(engine, game, Action.Shuttle(base))
        assertEquals(base, done.state.location)
        assertEquals(game.cash - Engine.SHUTTLE_FARE, done.state.cash)
        assertEquals(Clock.HOUR, done.state.clock.used)
        assertEquals(emptyList(), engine.shuttleStops(game.copy(location = colony)))
    }

    /** A new game with Duke and the starship at the port. */
    private fun landed(engine: Engine): GameState =
        engine.start(1).let { it.copy(location = port, ship = it.ship.copy(position = ShipPosition.InArea(port))) }

    private fun done(engine: Engine, state: GameState, action: Action): ActionResult.Done = assertIs(runSync { engine.perform(state, action) })
}
