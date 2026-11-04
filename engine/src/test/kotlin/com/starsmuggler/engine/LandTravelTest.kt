package com.starsmuggler.engine

import com.starsmuggler.engine.EngineTest.Companion.runSync
import com.starsmuggler.engine.EngineTest.Companion.start
import com.starsmuggler.engine.content.Air
import com.starsmuggler.engine.content.Area
import com.starsmuggler.engine.content.AreaType
import com.starsmuggler.engine.content.ConnectionPattern
import com.starsmuggler.engine.content.Connector
import com.starsmuggler.engine.content.ConnectorKind
import com.starsmuggler.engine.content.DiceTable
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LandTravelTest {
    private val engine = Engine(content())

    private val port = Location(SYSTEM, 1, "L", "port")
    private val ruins = Location(SYSTEM, 1, "L", "ruins")
    private val city = Location(SYSTEM, 1, "L", "city")
    private val colony = Location(SYSTEM, 1, "R", "colony")
    private val base = Location(SYSTEM, 1, "R", "base")

    @Test
    fun `routes follow the tile's lines and cross the planet's seam`() {
        assertEquals(
            listOf(
                LandRoute(ruins, ConnectorKind.TRIPLE, null),
                LandRoute(city, ConnectorKind.SINGLE, 2),
                LandRoute(colony, ConnectorKind.SINGLE, 3),
            ),
            engine.landRoutes(at(port)),
        )
        assertEquals(listOf(LandRoute(base, ConnectorKind.TRIPLE, null), LandRoute(port, ConnectorKind.SINGLE, 3)), engine.landRoutes(at(colony)))
    }

    @Test
    fun `on foot a triple line takes an hour and the entry is checked`() {
        val done = done(at(port), Action.Travel(TravelMode.ON_FOOT, ruins))
        assertEquals(ruins, done.state.location)
        assertEquals(Clock.HOUR, done.state.clock.used)
        assertTrue(done.entries.any { it is LogEntry.DetectionChecked && it.mode == "on_foot" })
    }

    @Test
    fun `on foot a single line takes a whole day per step`() {
        val game = at(port)
        assertEquals(Rejection.NOT_ENOUGH_HOURS, engine.check(game.copy(clock = game.clock.spend(1)), Action.Travel(TravelMode.ON_FOOT, city)))
        val first = done(game, Action.Travel(TravelMode.ON_FOOT, city)).state
        assertEquals(Trek(port, city, 1), first.trek)
        assertEquals(port, first.location)
        assertEquals(Clock.DAY, first.clock.used)
        assertEquals(Rejection.NOT_ENOUGH_HOURS, engine.check(first, Action.ContinueTrek))
        assertEquals(Rejection.ON_TREK, engine.check(first, Action.Travel(TravelMode.ON_FOOT, ruins)))
        val morning = done(first, Action.EndDay).state
        val arrived = done(morning, Action.ContinueTrek).state
        assertNull(arrived.trek)
        assertEquals(city, arrived.location)
    }

    @Test
    fun `a commercial vehicle is rented by the hour and must last the trip`() {
        val game = at(port)
        assertEquals(Rejection.NO_VEHICLE, engine.check(game, Action.Travel(TravelMode.COMMERCIAL, city)))
        val rented = done(game, Action.RentVehicle(6, 2))
        assertEquals(LogEntry.VehicleRented(rented.state.step, 6, 2, 4), rented.entries.last())
        assertEquals(game.cash - 4, rented.state.cash)
        val suited = rented.state
        assertEquals(Rejection.RENTAL_TOO_SHORT, engine.check(suited, Action.Travel(TravelMode.COMMERCIAL, colony)))
        val trip = done(suited, Action.Travel(TravelMode.COMMERCIAL, city)).state
        assertEquals(city, trip.location)
        assertEquals(2 * Clock.HOUR, trip.clock.used)
        assertEquals(Rejection.INVALID_VALUE, engine.check(game, Action.RentVehicle(5, 1)))
    }

    @Test
    fun `the symbol color limits the ways of travel`() {
        assertEquals(Rejection.RESTRICTED_AREA, engine.check(at(colony), Action.Travel(TravelMode.ON_FOOT, base)))
        assertEquals(Rejection.RESTRICTED_AREA, engine.check(withSkimmer(at(port)), Action.Travel(TravelMode.SKIMMER, ruins)))
    }

    @Test
    fun `bad air needs a working utility suit, which is then used`() {
        val game = done(at(port), Action.RentVehicle(3, 5)).state
        val suitless = game.copy(duke = game.duke.copy(items = emptyList()))
        assertEquals(Rejection.NO_LIFE_SUPPORT, engine.check(suitless, Action.Travel(TravelMode.COMMERCIAL, colony)))
        val done = done(game, Action.Travel(TravelMode.COMMERCIAL, colony))
        assertTrue(GearRef.DukeItem(0) in done.state.usedToday)
    }

    @Test
    fun `a skimmer needs a driver and breaks down in bad air`() {
        assertEquals(Rejection.GEAR_MISSING, engine.check(at(port), Action.Travel(TravelMode.SKIMMER, city)))
        val skimmer = withSkimmer(at(port))
        assertEquals(Rejection.NO_DRIVER, engine.check(skimmer, Action.Travel(TravelMode.SKIMMER, city)))
        val driven = skimmer.copy(duke = skimmer.duke.copy(skills = skimmer.duke.skills + Skill.DRIVER))
        val done = done(driven, Action.Travel(TravelMode.SKIMMER, colony))
        val ref = GearRef.DukeItem(driven.duke.items.lastIndex)
        assertEquals(3 * Clock.HOUR, done.state.clock.used)
        assertTrue(done.state.gear(ref)!!.broken)
        assertTrue(done.entries.any { it == LogEntry.BadAirBreakdown(done.state.step, ref) })
    }

    @Test
    fun `walking away from the ship leaves it behind`() {
        val game = at(port).let { it.copy(ship = it.ship.copy(position = ShipPosition.InArea(port))) }
        val away = done(game, Action.Travel(TravelMode.ON_FOOT, ruins)).state
        assertEquals(Rejection.NOT_ABOARD, engine.check(away, Action.Sublight(ShipPosition.Orbit(SYSTEM, 1))))
        assertEquals(away, Json.decodeFromString<GameState>(Json.encodeToString(away)))
    }

    private fun at(place: Location): GameState = engine.start(1).copy(location = place)

    private fun withSkimmer(game: GameState) = game.copy(duke = game.duke.copy(items = game.duke.items + Item(LandTravel.SKIMMER, 1)))

    private fun done(state: GameState, action: Action): ActionResult.Done = assertIs(runSync { engine.perform(state, action) })

    companion object {
        private const val SYSTEM = "palatek"

        private fun area(id: String, type: AreaType, restriction: Restriction, ownAir: Boolean = false) =
            Area(id, type, restriction, ownAir = ownAir, position = MapPoint(0.5f, 0.5f))

        private val TABLES = listOf(
            Json.decodeFromString<DiceTable>(
                """{"id":"r238","rule":"r238","rows":[
                   {"key":"on_foot","entries":[{"roll":"0+","result":"1"}]},
                   {"key":"commercial_vehicle","entries":[{"roll":"0+","result":"1"}]},
                   {"key":"skimmer","entries":[{"roll":"0+","result":"1"}]}]}""",
            ),
            Json.decodeFromString<DiceTable>(
                """{"id":"r233","rule":"r233","dice":"1d6","rows":[{"key":"colony","entries":[{"roll":"0+","result":"e011"}]}]}""",
            ),
        )

        fun content() = GameContent(
            sector = Sector(
                "test",
                RuleRef("r207"),
                systems = listOf(
                    StarSystem(SYSTEM, RuleRef("r207b"), wealth = 30, tech = 50, entryEvents = List(6) { EventRef("e092") }, planets = listOf(Planet(1, listOf("L", "R"), 7, Air.BAD))),
                ),
                patterns = listOf(ConnectionPattern(Sector.STANDARD_PATTERN, RuleRef("r234"), nodes = listOf(com.starsmuggler.engine.content.PatternNode(SYSTEM, MapPoint(0.5f, 0.5f))), routes = emptyList())),
            ),
            tiles = listOf(
                Tile(
                    "L",
                    PlanetSide.LEFT,
                    "tiles/l.webp",
                    areas = listOf(
                        area("port", AreaType.SPACEPORT, Restriction.WHITE, ownAir = true),
                        area("ruins", AreaType.RUINS, Restriction.RED, ownAir = true),
                        area("city", AreaType.CITY, Restriction.YELLOW, ownAir = true),
                    ),
                    connectors = listOf(
                        Connector("port", to = "ruins", kind = ConnectorKind.TRIPLE),
                        Connector("port", to = "city", kind = ConnectorKind.SINGLE, steps = 2),
                        Connector("port", edgeSlot = 0, kind = ConnectorKind.SINGLE, steps = 3),
                    ),
                ),
                Tile(
                    "R",
                    PlanetSide.RIGHT,
                    "tiles/r.webp",
                    areas = listOf(area("colony", AreaType.COLONY, Restriction.WHITE), area("base", AreaType.SPACE_STATION, Restriction.PURPLE)),
                    connectors = listOf(
                        Connector("colony", to = "base", kind = ConnectorKind.TRIPLE),
                        Connector("colony", edgeSlot = 0, kind = ConnectorKind.SINGLE),
                    ),
                ),
            ),
            tables = TABLES,
            factors = listOf(FactorTable("r238.area", RuleRef("r238"), emptyMap())),
            paragraphs = emptyList(),
            equipment = emptyList(),
            goods = emptyList(),
            events = emptyList(),
        )
    }
}
