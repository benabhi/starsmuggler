package com.starsmuggler.content

import com.starsmuggler.engine.content.Air
import com.starsmuggler.engine.content.AreaType
import com.starsmuggler.engine.content.ConnectorKind
import com.starsmuggler.engine.content.DurationKind
import com.starsmuggler.engine.content.EventRef
import com.starsmuggler.engine.content.Outcome
import com.starsmuggler.engine.content.ParagraphKind
import com.starsmuggler.engine.content.RuleRef
import com.starsmuggler.engine.content.Seam
import com.starsmuggler.engine.content.Tile
import java.io.File
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ContentLoaderTest {
    private val assets = File("src/main/assets")
    private val files = ContentSource { path -> File(assets, path).readText() }

    /** The real assets with one file replaced. */
    private fun replacing(path: String, text: String) = ContentSource { if (it == path) text else files.read(it) }

    @Test
    fun `the shipped content loads`() {
        val content = ContentLoader(files).load()
        val regari = content.game.system("regari")
        assertEquals(0, regari.wealth)
        assertEquals(30, regari.tech)
        assertEquals(EventRef("e090"), regari.entryEvent(1))
        assertEquals(listOf("J", "K"), regari.planets.single().tiles)
        assertEquals(Air.GOOD, regari.planets.single().air)
        assertEquals(listOf("palatek"), content.game.pattern().neighbors("regari"))
        assertEquals("Regari", content.names.system("regari"))
    }

    @Test
    fun `the sector has the ten systems of r207`() {
        val game = ContentLoader(files).load().game
        assertEquals(
            listOf("regari", "palatek", "byzantium", "imperia", "talitar", "cubro", "uruskop", "nipna", "mynkuria", "new_karma"),
            game.systems.keys.toList(),
        )
        assertEquals((0 until 10).map { 'a' + it }.map { "r207$it" }, game.systems.values.map { it.rule.id })
        assertEquals(setOf("imperia", "uruskop", "mynkuria"), game.systems.values.filter { it.asteroids }.map { it.id }.toSet())
        val nipna = game.system("nipna")
        assertEquals(100, nipna.tech)
        assertEquals(Air.NONE, nipna.planets[1].air)
        assertEquals(listOf("A", "I"), nipna.planets[1].tiles)
    }

    @Test
    fun `the standard pattern joins every system as in r234`() {
        val pattern = ContentLoader(files).load().game.pattern()
        assertEquals(13, pattern.routes.size)
        assertEquals(setOf("mynkuria", "new_karma", "uruskop", "talitar"), pattern.neighbors("nipna").toSet())
        assertEquals(listOf("talitar"), pattern.neighbors("cubro"))
        // Every system can be reached from Regari.
        val seen = mutableSetOf("regari")
        val queue = ArrayDeque(seen)
        while (queue.isNotEmpty()) pattern.neighbors(queue.removeFirst()).filter { seen.add(it) }.forEach(queue::addLast)
        assertEquals(10, seen.size)
    }

    @Test
    fun `every system has a Spanish name and a row in the system table`() {
        val content = ContentLoader(files).load()
        val systemTable = content.game.table("r233.system")
        for (id in content.game.systems.keys) {
            assertTrue(content.names.systems[id]?.isNotBlank() == true, "No name for system $id")
            assertTrue(systemTable.row(id) != null, "No SIST row for system $id")
        }
        assertEquals("Nueva Karma", content.names.system("new_karma"))
    }

    @Test
    fun `a route to an unknown system is rejected`() {
        val broken = File(assets, ContentLoader.SECTOR).readText().replace("\"b\": \"regari\"", "\"b\": \"regary\"")
        val error = assertFailsWith<ContentException> { ContentLoader(replacing(ContentLoader.SECTOR, broken)).load() }
        assertTrue("regary" in error.message!!, error.message)
    }

    @Test
    fun `every system finds its price and tech rows`() {
        val game = ContentLoader(files).load().game
        for (system in game.systems.values) {
            assertTrue(game.table("r241").row(system.wealth.toString()) != null, "No r241 row for wealth ${system.wealth}")
            assertTrue(game.table("r235").row(system.tech.toString()) != null, "No r235 row for tech ${system.tech}")
            assertTrue(game.table("r238").lookup(system.tech, "starship") != null, "No r238 entry for tech ${system.tech}")
        }
    }

    @Test
    fun `tables read as the rule book prints them`() {
        val game = ContentLoader(files).load().game
        assertEquals(16, game.table("r233").rows.size)
        assertEquals(Outcome.Event(EventRef("e417")), game.table("r233").lookup(0, "prison"))
        assertEquals(Outcome.Value(1, marked = true), game.table("r235").lookup(1, "10"))
        assertEquals(Outcome.Value(5), game.table("r238").lookup(50, "starship"))
        assertEquals(Outcome.Multiplier(40), game.table("r241").lookup(6, "100"))
        assertEquals(Outcome.Rule(RuleRef("r306")), game.table("r327").lookup(0))
        assertEquals(Outcome.Rule(RuleRef("r300")), game.table("r327").lookup(14))
        assertEquals(Outcome.Value(3), game.table("r242.time").lookup(400))
        assertEquals(5, game.factors("r242.base").factor("spaceport"))
        assertEquals(-2, game.factors("r238.area").factor("craters"))
    }

    @Test
    fun `rule paragraphs r300 to r339 are all there`() {
        val game = ContentLoader(files).load().game
        assertEquals((300..339).map { "r$it" }, game.paragraphs.keys.toList())
        assertEquals(40, game.paragraph(RuleRef("r321")).basePrice)
        assertEquals(ParagraphKind.BUY, game.paragraph(RuleRef("r333")).kind)
        assertEquals(19, game.equipment.size)
    }

    @Test
    fun `every named id has a Spanish name`() {
        val content = ContentLoader(files).load()
        val game = content.game
        for (id in game.equipment.keys) assertTrue(content.names.equipment[id]?.isNotBlank() == true, "No name for equipment $id")
        for (id in game.goods.keys) assertTrue(content.names.goods[id]?.isNotBlank() == true, "No name for goods $id")
        for (mode in game.table("r238").rows.mapNotNull { it.key }) {
            assertTrue(content.names.travelModes[mode]?.isNotBlank() == true, "No name for travel mode $mode")
        }
        val keys = game.tables.values.flatMap { t -> t.rows.flatMap { r -> r.entries.mapNotNull { (it.result as? Outcome.Key)?.key } } }
        for (key in keys) {
            assertTrue(key in content.names.results || key in content.names.goods, "No name for result #$key")
        }
    }

    @Test
    fun `a paragraph selling unknown goods is rejected`() {
        val broken = File(assets, ContentLoader.PARAGRAPHS).readText().replace("\"rare_birds\"", "\"rare_bird\"")
        val error = assertFailsWith<ContentException> { ContentLoader(replacing(ContentLoader.PARAGRAPHS, broken)).load() }
        assertTrue("rare_bird" in error.message!!, error.message)
    }

    @Test
    fun `every event has its duration and its Spanish text`() {
        val content = ContentLoader(files).load()
        val expected = ((1..199) + (400..431)).map { "e%03d".format(it) }
        assertEquals(expected, content.game.events.keys.sorted())
        assertEquals(expected, content.texts.events.keys.sorted())
        assertEquals(DurationKind.HOURS, content.game.event(EventRef("e001")).duration.kind)
        assertEquals(0, content.game.event(EventRef("e001")).duration.hours)
        assertTrue(content.game.event(EventRef("e027")).duration.defaulted)
        assertEquals("El comienzo de la aventura", content.texts.event("e001")!!.title)
        assertEquals("Comprar y vender artículos", content.texts.event("e027")!!.group)
    }

    @Test
    fun `every rule and event the data names has a text`() {
        val content = ContentLoader(files).load()
        val game = content.game
        val rules = buildList {
            add(game.sector.rule)
            game.systems.values.forEach { add(it.rule) }
            game.sector.patterns.forEach { add(it.rule) }
            game.tables.values.forEach { add(it.rule) }
            game.factors.values.forEach { add(it.rule) }
            game.paragraphs.values.forEach { add(it.rule) }
            game.equipment.values.forEach { add(it.rule) }
            game.goods.values.forEach { add(it.rule) }
            AreaType.entries.forEach { add(it.rule) }
        }.map { it.id }
        val results = game.tables.values.flatMap { t -> t.rows.flatMap { r -> r.entries.map { it.result } } }
        val referenced = rules + results.filterIsInstance<Outcome.Rule>().map { it.ref.id }
        // r295a is printed in the r233 City row but does not exist in the rule book (translation note 10).
        val missingRules = referenced.toSet().filter { content.texts.rule(it) == null } - setOf("r295a")
        assertEquals(emptyList(), missingRules)
        val events = results.filterIsInstance<Outcome.Event>().map { it.ref.id } + game.systems.values.flatMap { s -> s.entryEvents.map { it.id } }
        assertEquals(emptyList(), events.toSet().filter { content.texts.event(it) == null })
    }

    @Test
    fun `texts are clean and sections list their subsections`() {
        val texts = ContentLoader(files).load().texts
        assertEquals(192, texts.rules.size)
        assertEquals((0 until 10).map { "r207" + ('a' + it) }, texts.rule("r207")!!.children)
        assertEquals("r207", texts.rule("r207a")!!.parent)
        assertEquals("Regari", texts.rule("r207a")!!.title)
        for ((id, entry) in texts.rules + texts.events) {
            assertTrue(entry.title.isNotBlank(), "No title for $id")
            assertTrue(entry.blocks.isNotEmpty() || entry.children.isNotEmpty(), "No text for $id")
            for (block in entry.blocks) assertTrue("<!--" !in block.toString(), "Page marker left in $id")
        }
        val table = texts.rule("r241")!!.blocks.filterIsInstance<TextBlock.Table>().single()
        assertEquals(12, table.rows.size)
    }

    @Test
    fun `the twelve tiles are there and every planet closes its seam`() {
        val game = ContentLoader(files).load().game
        assertEquals(listOf("A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "asteroids"), game.tiles.keys.toList())
        for (tile in game.tiles.values.filter { it.planetSide != null }) {
            assertEquals((0 until Tile.EDGE_SLOTS).toList(), tile.connectors.mapNotNull { it.edgeSlot }.sorted(), "Tile ${tile.id} edge slots")
        }
        // Every planet of r207 joins its two tiles at three seams.
        for (planet in game.systems.values.flatMap { it.planets }) assertEquals(3, game.seams(planet).size, "Planet ${planet.tiles}")
        assertEquals(7, game.tile("asteroids").areas.size)
    }

    @Test
    fun `seams join the tiles as the scans show`() {
        val game = ContentLoader(files).load().game
        // Regari, J-K: the halves face each other.
        val regari = game.seams(game.system("regari").planets.single())
        assertEquals(Seam("J", "ruins", "K", "spaceport", ConnectorKind.SINGLE, 10), regari[0])
        assertEquals(Seam("J", "industrial", "K", "mining", ConnectorKind.TRIPLE, null), regari[1])
        assertEquals(Seam("J", "rural", "K", "spaceport", ConnectorKind.SINGLE, 5), regari[2])
        // Cubro, K-A: both halves are on the right, so A is turned around.
        val cubro = game.seams(game.system("cubro").planets.single())
        assertEquals(Seam("K", "spaceport", "A", "mining", ConnectorKind.SINGLE, 10), cubro[0])
        assertEquals(Seam("K", "spaceport", "A", "industrial", ConnectorKind.SINGLE, 7), cubro[2])
    }

    @Test
    fun `a tile image exists for every tile`() {
        val game = ContentLoader(files).load().game
        for (tile in game.tiles.values) assertTrue(File("../app/src/main/assets", tile.image).isFile, "No image ${tile.image}")
    }

    @Test
    fun `tiles and tables are reachable by id`() {
        val game = ContentLoader(files).load().game
        val colony = game.tile("J").area("colony")!!
        assertEquals(AreaType.COLONY, colony.type)
        assertTrue(colony.bank && colony.medical)
        assertEquals(Outcome.SystemTable, game.table("r233").lookup(6, "city"))
        assertEquals(Outcome.Event(EventRef("e192")), game.table("r233.system").lookup(6, "regari"))
    }

    @Test
    fun `every area type has a Spanish name`() {
        val names = ContentLoader(files).load().names
        for (type in AreaType.entries) {
            assertTrue(names.areaTypes[type.key]?.isNotBlank() == true, "No name for area type ${type.key}")
        }
    }

    @Test
    fun `an unknown key names the file`() {
        val broken = File(assets, ContentLoader.EQUIPMENT).readText().replace("\"capacity\"", "\"capacty\"")
        val error = assertFailsWith<ContentException> { ContentLoader(replacing(ContentLoader.EQUIPMENT, broken)).load() }
        assertEquals(ContentLoader.EQUIPMENT, error.path)
    }

    @Test
    fun `a bad event id names the file`() {
        val broken = File(assets, ContentLoader.SECTOR).readText().replace("\"e090\"", "\"x090\"")
        val error = assertFailsWith<ContentException> { ContentLoader(replacing(ContentLoader.SECTOR, broken)).load() }
        assertEquals(ContentLoader.SECTOR, error.path)
        assertTrue("x090" in error.message!!, error.message)
    }

    @Test
    fun `another format version is rejected`() {
        val newer = File(assets, ContentLoader.TILES).readText().replace("\"formatVersion\": 1", "\"formatVersion\": 2")
        val error = assertFailsWith<ContentException> { ContentLoader(replacing(ContentLoader.TILES, newer)).load() }
        assertTrue("format version 2" in error.message!!, error.message)
    }

    @Test
    fun `a missing file names the file`() {
        val source = ContentSource { if (it == ContentLoader.TABLES) throw java.io.FileNotFoundException(it) else files.read(it) }
        val error = assertFailsWith<ContentException> { ContentLoader(source).load() }
        assertEquals(ContentLoader.TABLES, error.path)
    }
}
