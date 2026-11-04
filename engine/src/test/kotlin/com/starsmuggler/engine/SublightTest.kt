package com.starsmuggler.engine

import com.starsmuggler.engine.EngineTest.Companion.runSync
import com.starsmuggler.engine.EngineTest.Companion.start
import com.starsmuggler.engine.content.Air
import com.starsmuggler.engine.content.Area
import com.starsmuggler.engine.content.ConnectionPattern
import com.starsmuggler.engine.content.DiceTable
import com.starsmuggler.engine.content.AreaType
import com.starsmuggler.engine.content.EventRef
import com.starsmuggler.engine.content.FactorTable
import com.starsmuggler.engine.content.GameContent
import com.starsmuggler.engine.content.MapPoint
import com.starsmuggler.engine.content.Planet
import com.starsmuggler.engine.content.PlanetSide
import com.starsmuggler.engine.content.Restriction
import com.starsmuggler.engine.content.RuleRef
import com.starsmuggler.engine.content.Sector
import com.starsmuggler.engine.content.StarSystem
import com.starsmuggler.engine.content.Tile
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SublightTest {
    private val engine = Engine(content())

    private val start = Location.START
    private val orbit1 = ShipPosition.Orbit(REGARI, 1)
    private val belt = ShipPosition.Orbit(REGARI, Location.ASTEROID_BELT)
    private val station1 = ShipPosition.InArea(Location(REGARI, 1, "J", "space_station"))
    private val colony1 = ShipPosition.InArea(Location(REGARI, 1, "J", "colony"))
    private val asteroid = ShipPosition.InArea(Location(REGARI, Location.ASTEROID_BELT, Location.ASTEROID_TILE, "asteroid_1"))
    private val beltStation = ShipPosition.InArea(Location(REGARI, Location.ASTEROID_BELT, Location.ASTEROID_TILE, "space_station"))

    @Test
    fun `the ship starts landed at Regari's spaceport with Duke`() {
        val game = engine.start(1)
        assertEquals(ShipPosition.InArea(start), game.ship.position)
        assertTrue(game.dukeAboard)
    }

    @Test
    fun `from the surface the ship rises to the orbit or straight to a station`() {
        assertEquals(listOf(orbit1, station1), engine.sublightDestinations(engine.start(1)))
    }

    @Test
    fun `a move takes one hour, carries Duke and uses the ship`() {
        val done = done(engine.start(1), Action.Sublight(orbit1))
        assertEquals(orbit1, done.state.ship.position)
        assertEquals(Clock.HOUR, done.state.clock.used)
        assertEquals(listOf(GearRef.Starship), done.state.usedToday)
        assertEquals(LogEntry.ShipMoved(done.state.step, ShipPosition.InArea(start), orbit1), done.entries.last())
        assertTrue(done.state.dukeAboard)
    }

    @Test
    fun `from orbit the ship goes outward, to another orbit, or down to a white or purple area`() {
        val game = at(orbit1)
        assertEquals(
            listOf(
                ShipPosition.Outward(REGARI, 1),
                ShipPosition.Orbit(REGARI, 2),
                belt,
                station1,
                colony1,
                ShipPosition.InArea(start),
            ),
            engine.sublightDestinations(game),
        )
    }

    @Test
    fun `landing moves Duke to the area`() {
        val done = done(at(orbit1), Action.Sublight(colony1))
        assertEquals(colony1.location, done.state.location)
        assertTrue(done.state.dukeAboard)
    }

    @Test
    fun `a docked ship goes down to the surface or up to the orbit`() {
        val game = done(engine.start(1), Action.Sublight(station1)).state
        assertEquals(listOf(orbit1, colony1, ShipPosition.InArea(start)), engine.sublightDestinations(game))
    }

    @Test
    fun `outward goes up to four hours, and inward from one hour reaches any orbit`() {
        assertEquals(listOf(ShipPosition.Outward(REGARI, 2), orbit1, ShipPosition.Orbit(REGARI, 2), belt), engine.sublightDestinations(at(ShipPosition.Outward(REGARI, 1))))
        assertEquals(
            listOf(ShipPosition.Outward(REGARI, 4), ShipPosition.Outward(REGARI, 2)),
            engine.sublightDestinations(at(ShipPosition.Outward(REGARI, 3))),
        )
        assertEquals(listOf(ShipPosition.Outward(REGARI, 3)), engine.sublightDestinations(at(ShipPosition.Outward(REGARI, 4))))
    }

    @Test
    fun `the asteroids and their station only join through the orbit`() {
        assertEquals(
            listOf(ShipPosition.Outward(REGARI, 1), orbit1, ShipPosition.Orbit(REGARI, 2), asteroid, beltStation),
            engine.sublightDestinations(at(belt)),
        )
        assertEquals(listOf(belt), engine.sublightDestinations(at(asteroid)))
        assertEquals(listOf(belt), engine.sublightDestinations(at(beltStation)))
    }

    @Test
    fun `a place out of reach is rejected`() {
        assertEquals(Rejection.UNREACHABLE, engine.check(engine.start(1), Action.Sublight(belt)))
        assertEquals(Rejection.UNREACHABLE, engine.check(engine.start(1), Action.Sublight(ShipPosition.InArea(Location(REGARI, 1, "K", "city")))))
    }

    @Test
    fun `the sublight drive needs a hypercharge, a working ship, Duke aboard and an hour`() {
        val game = engine.start(1)
        val move = Action.Sublight(orbit1)
        assertEquals(Rejection.NO_HYPERCHARGES, engine.check(game.copy(ship = game.ship.copy(hypercharges = 0)), move))
        assertEquals(Rejection.GEAR_BROKEN, engine.check(game.copy(ship = game.ship.copy(broken = true)), move))
        assertEquals(Rejection.NOT_ABOARD, engine.check(game.copy(location = colony1.location), move))
        assertEquals(Rejection.NOT_ENOUGH_HOURS, engine.check(game.copy(clock = game.clock.spend(Clock.DAY - 5)), move))
        assertEquals(null, engine.check(game.copy(clock = game.clock.spend(Clock.DAY - Clock.HOUR)), move))
    }

    @Test
    fun `the ship's position is saved with the game`() {
        val game = done(at(orbit1), Action.Sublight(ShipPosition.Outward(REGARI, 1))).state
        assertEquals(game, Json.decodeFromString<GameState>(Json.encodeToString(game)))
    }

    /** A new game with the ship (and so Duke) at [position]. */
    private fun at(position: ShipPosition): GameState {
        val game = engine.start(1)
        val location = (position as? ShipPosition.InArea)?.location ?: game.location
        return game.copy(ship = game.ship.copy(position = position), location = location)
    }

    private fun done(state: GameState, action: Action): ActionResult.Done = assertIs(runSync { engine.perform(state, action) })

    companion object {
        const val REGARI = "regari"

        /** Detection by starship (r238): 4 at Regari's tech 30, +2 at a station, -1 at ruins. */
        private val ENTRY_TABLES = listOf(
            Json.decodeFromString<DiceTable>(
                """{"id":"r238","rule":"r238","rows":[{"key":"starship","entries":[
                   {"roll":"24-","result":"3"},{"roll":"25-39","result":"4"},{"roll":"40-69","result":"5"},{"roll":"70+","result":"6"}]}]}""",
            ),
            Json.decodeFromString<DiceTable>(
                """{"id":"r233","rule":"r233","dice":"1d6","rows":[
                   {"key":"spaceport","entries":[{"roll":"0-2","result":"e011"},{"roll":"3-5","result":"SIST"},{"roll":"6+","result":"e185"}]},
                   {"key":"colony","entries":[{"roll":"0-2","result":"e011"},{"roll":"3-5","result":"SIST"},{"roll":"6+","result":"e185"}]},
                   {"key":"space_station","entries":[{"roll":"0-2","result":"e011"},{"roll":"3-5","result":"SIST"},{"roll":"6+","result":"e185"}]}]}""",
            ),
            Json.decodeFromString<DiceTable>(
                """{"id":"r233.system","rule":"r233","dice":"1d6","rows":[{"key":"regari","entries":[{"roll":"1-6","result":"e122"}]}]}""",
            ),
        )
        private val DETECTION_AREAS = FactorTable("r238.area", RuleRef("r238"), mapOf("space_station" to 2, "ruins" to -1))

        private fun area(id: String, type: AreaType, restriction: Restriction) = Area(id, type, restriction, position = MapPoint(0.5f, 0.5f))

        /** Regari with two planets and the asteroids, and any [others] that a jump [pattern] needs. */
        fun content(
            others: List<StarSystem> = emptyList(),
            pattern: ConnectionPattern? = null,
            tables: List<DiceTable> = emptyList(),
        ) = GameContent(
            sector = Sector(
                "test",
                RuleRef("r207"),
                systems = others + listOf(
                    StarSystem(
                        REGARI,
                        RuleRef("r207a"),
                        wealth = 0,
                        tech = 30,
                        entryEvents = List(6) { EventRef("e090") },
                        planets = listOf(Planet(1, listOf("J", "K"), 3, Air.GOOD), Planet(2, listOf("J", "K"), 9, Air.NONE)),
                        asteroids = true,
                    ),
                ),
                patterns = listOfNotNull(pattern),
            ),
            tiles = listOf(
                Tile(
                    "J",
                    PlanetSide.LEFT,
                    "tiles/j.webp",
                    areas = listOf(area("space_station", AreaType.SPACE_STATION, Restriction.PURPLE), area("colony", AreaType.COLONY, Restriction.WHITE)),
                    connectors = emptyList(),
                ),
                Tile(
                    "K",
                    PlanetSide.RIGHT,
                    "tiles/k.webp",
                    areas = listOf(
                        area("spaceport", AreaType.SPACEPORT, Restriction.WHITE),
                        area("city", AreaType.CITY, Restriction.YELLOW),
                        area("ruins", AreaType.RUINS, Restriction.RED),
                    ),
                    connectors = emptyList(),
                ),
                Tile(
                    Location.ASTEROID_TILE,
                    null,
                    "tiles/asteroids.webp",
                    areas = listOf(area("asteroid_1", AreaType.ASTEROID, Restriction.PURPLE), area("space_station", AreaType.SPACE_STATION, Restriction.PURPLE)),
                    connectors = emptyList(),
                ),
            ),
            tables = tables + ENTRY_TABLES,
            factors = listOf(DETECTION_AREAS),
            paragraphs = emptyList(),
            equipment = emptyList(),
            goods = emptyList(),
            events = emptyList(),
        )
    }
}
