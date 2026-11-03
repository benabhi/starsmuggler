package com.starsmuggler.content

import com.starsmuggler.engine.content.Air
import com.starsmuggler.engine.content.AreaType
import com.starsmuggler.engine.content.EventRef
import com.starsmuggler.engine.content.Outcome
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
