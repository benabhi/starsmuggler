package com.starsmuggler.engine

import com.starsmuggler.engine.EngineTest.Companion.runSync
import com.starsmuggler.engine.EngineTest.Companion.start
import com.starsmuggler.engine.SublightTest.Companion.REGARI
import com.starsmuggler.engine.content.Air
import com.starsmuggler.engine.content.ConnectionPattern
import com.starsmuggler.engine.content.DiceTable
import com.starsmuggler.engine.content.EventRef
import com.starsmuggler.engine.content.MapPoint
import com.starsmuggler.engine.content.PatternNode
import com.starsmuggler.engine.content.Planet
import com.starsmuggler.engine.content.Route
import com.starsmuggler.engine.content.RuleRef
import com.starsmuggler.engine.content.Sector
import com.starsmuggler.engine.content.StarSystem
import com.starsmuggler.engine.dice.DiceHand
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HyperspaceTest {
    private val engine = Engine(content())
    private val outward4 = ShipPosition.Outward(REGARI, 4)

    @Test
    fun `jumps reach up to three systems along the routes at 1, 3 and 5 hypercharges`() {
        assertEquals(
            listOf(JumpOption(PALATEK, 1, 1, 5), JumpOption(IMPERIA, 2, 3, 5), JumpOption(TALITAR, 3, 5, 5)),
            engine.jumpOptions(engine.start(1)),
        )
        assertEquals(Rejection.UNREACHABLE, engine.check(engine.start(1), Action.Jump(CUBRO)))
    }

    @Test
    fun `the risk falls from the surface to four moves outward`() {
        val game = engine.start(1)
        assertEquals(5, risk(game))
        assertEquals(4, risk(at(game, ShipPosition.Orbit(REGARI, 1))))
        assertEquals(4, risk(at(game, ShipPosition.InArea(Location(REGARI, 1, "J", "space_station")))))
        assertEquals(3, risk(at(game, ShipPosition.Outward(REGARI, 1))))
        assertEquals(1, risk(at(game, ShipPosition.Outward(REGARI, 3))))
        assertEquals(0, risk(at(game, outward4)))
    }

    @Test
    fun `a sure jump rolls nothing and arrives four moves outward`() {
        val done = done(at(engine.start(1), outward4), Action.Jump(IMPERIA), DiceHand.physical { error("No roll at risk 0") })
        assertEquals(ShipPosition.Outward(IMPERIA, 4), done.state.ship.position)
        assertEquals(3, done.state.ship.hypercharges)
        assertEquals(0, done.state.clock.used)
        assertEquals(listOf(GearRef.Starship), done.state.usedToday)
        assertEquals(LogEntry.Jumped(done.state.step, REGARI, IMPERIA, 2, 3, 0, null, succeeded = true), done.entries.last())
    }

    @Test
    fun `a roll above the risk jumps safely`() {
        val done = done(at(engine.start(1), ShipPosition.Orbit(REGARI, 1)), Action.Jump(PALATEK), faces(5))
        assertEquals(ShipPosition.Outward(PALATEK, 4), done.state.ship.position)
        assertNull(done.state.ending)
    }

    @Test
    fun `without a stasis unit a dissipation kills Duke`() {
        val game = at(engine.start(1), ShipPosition.Orbit(REGARI, 1)).let { it.copy(ship = it.ship.copy(stasis = 0)) }
        val done = done(game, Action.Jump(PALATEK), faces(4))
        assertEquals(LogEntry.Dissipated(done.state.step, PALATEK, inStasis = false), done.entries.first { it is LogEntry.Dissipated })
        assertEquals(Ending.DIED, done.state.ending)
    }

    @Test
    fun `in stasis Duke may die on r240`() {
        val done = done(at(engine.start(1), ShipPosition.Orbit(REGARI, 1)), Action.Jump(PALATEK), faces(4, 2))
        assertEquals(Ending.DIED, done.state.ending)
    }

    @Test
    fun `waking from stasis Duke has lost his ship and gear, and the money as r240 says`() {
        val game = at(engine.start(1), ShipPosition.Orbit(REGARI, 1)).copy(cash = 1000)
        val done = done(game, Action.Jump(PALATEK), faces(1, 5))
        val state = done.state
        val place = Location(PALATEK, 1, "J", "space_station")
        assertNull(state.ending)
        assertTrue(state.ship.destroyed)
        assertEquals(place, state.location)
        assertEquals(500, state.cash)
        assertEquals(emptyList(), state.duke.items)
        assertEquals(null, state.gear(GearRef.Starship))
        assertEquals(LogEntry.StasisRecovered(state.step, place, 500), done.entries.last())
        assertEquals(Rejection.GEAR_MISSING, engine.check(state, Action.Jump(REGARI)))
        assertEquals(state, Json.decodeFromString<GameState>(Json.encodeToString(state)))
    }

    @Test
    fun `a jump needs its hypercharges and Duke aboard`() {
        val game = engine.start(1)
        assertEquals(Rejection.NO_HYPERCHARGES, engine.check(game.copy(ship = game.ship.copy(hypercharges = 4)), Action.Jump(TALITAR)))
        assertEquals(null, engine.check(game.copy(ship = game.ship.copy(hypercharges = 3)), Action.Jump(IMPERIA)))
        assertEquals(Rejection.NOT_ABOARD, engine.check(game.copy(location = Location(REGARI, 1, "J", "colony")), Action.Jump(PALATEK)))
        assertEquals(Rejection.GEAR_BROKEN, engine.check(game.copy(ship = game.ship.copy(broken = true)), Action.Jump(PALATEK)))
    }

    private fun risk(game: GameState) = engine.jumpOptions(game).first().risk

    private fun at(game: GameState, position: ShipPosition): GameState =
        game.copy(ship = game.ship.copy(position = position), location = (position as? ShipPosition.InArea)?.location ?: game.location)

    private fun faces(vararg values: Int): DiceHand {
        val queue = ArrayDeque(values.toList())
        return DiceHand.physical { request -> List(request.dice.count) { queue.removeFirst() } }
    }

    private fun done(state: GameState, action: Action, hand: DiceHand = DiceHand.APP): ActionResult.Done =
        assertIs(runSync { engine.perform(state, action, hand) })

    companion object {
        private const val PALATEK = "palatek"
        private const val IMPERIA = "imperia"
        private const val TALITAR = "talitar"
        private const val CUBRO = "cubro"

        private val STASIS = Json.decodeFromString<DiceTable>(
            """{"id":"r240","rule":"r240","dice":"1d6","rows":[{"entries":[
               {"roll":"1","result":"#stasis_lost"},{"roll":"2","result":"#stasis_culture_shock"},
               {"roll":"3","result":"#stasis_1200_years"},{"roll":"4","result":"#stasis_230_years"},
               {"roll":"5","result":"#stasis_3_years"},{"roll":"6","result":"#stasis_1_year"}]}]}""",
        )

        private fun system(id: String) =
            StarSystem(id, RuleRef("r207b"), wealth = 0, tech = 0, entryEvents = List(6) { EventRef("e090") }, planets = listOf(Planet(1, listOf("J", "K"), 5, Air.GOOD)))

        fun content() = SublightTest.content(
            others = listOf(PALATEK, IMPERIA, TALITAR, CUBRO).map(::system),
            pattern = ConnectionPattern(
                Sector.STANDARD_PATTERN,
                RuleRef("r234"),
                nodes = listOf(REGARI, PALATEK, IMPERIA, TALITAR, CUBRO).map { PatternNode(it, MapPoint(0.5f, 0.5f)) },
                routes = listOf(Route(REGARI, PALATEK), Route(PALATEK, IMPERIA), Route(IMPERIA, TALITAR), Route(TALITAR, CUBRO)),
            ),
            tables = listOf(STASIS),
        )
    }
}
