package com.starsmuggler.engine.content

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ContentModelTest {
    @Test
    fun `rule and event ids are checked`() {
        assertEquals("r207a", RuleRef("r207a").id)
        assertEquals("e090", EventRef("e090").id)
        assertFailsWith<IllegalArgumentException> { RuleRef("e090") }
        assertFailsWith<IllegalArgumentException> { RuleRef("r20") }
        assertFailsWith<IllegalArgumentException> { EventRef("e90") }
    }

    @Test
    fun `roll ranges parse single, closed and open forms`() {
        assertEquals(RollRange(3, 3), RollRange.parse("3"))
        assertEquals(RollRange(1, 2), RollRange.parse("1-2"))
        val open = RollRange.parse("6+")
        assertTrue(9 in open)
        assertEquals("6+", open.toString())
        assertFailsWith<IllegalArgumentException> { RollRange.parse("x") }
    }

    @Test
    fun `table results parse every kind`() {
        assertEquals(Outcome.Event(EventRef("e129")), Outcome.parse("e129"))
        assertEquals(Outcome.Rule(RuleRef("r295a")), Outcome.parse("r295a"))
        assertEquals(Outcome.SystemTable, Outcome.parse("SIST"))
        assertEquals(Outcome.NoMoreRolls, Outcome.parse("stop"))
        assertEquals(Outcome.Value(4), Outcome.parse("4"))
        assertFailsWith<IllegalArgumentException> { Outcome.parse("maybe") }
    }

    @Test
    fun `a table looks up modified rolls`() {
        val table = Json.decodeFromString<DiceTable>(
            """{"id":"t","rule":"r233","dice":"1d6","rows":[{"key":"city","entries":[
               {"roll":"0","result":"e129"},{"roll":"1-5","result":"stop"},{"roll":"6+","result":"SIST"}]}]}""",
        )
        assertEquals(Outcome.Event(EventRef("e129")), table.lookup(0, "city"))
        assertEquals(Outcome.SystemTable, table.lookup(8, "city"))
        assertEquals(null, table.lookup(3, "spaceport"))
    }

    @Test
    fun `overlapping rolls are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            Json.decodeFromString<TableRow>("""{"entries":[{"roll":"1-3","result":"stop"},{"roll":"3","result":"stop"}]}""")
        }
    }

    @Test
    fun `a bad id in the data fails while reading`() {
        val error = runCatching { Json.decodeFromString<Equipment>("""{"id":"x","rule":"207"}""") }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException || error is SerializationException, "Unexpected $error")
    }

    @Test
    fun `connectors must join known areas`() {
        val point = MapPoint(0.5f, 0.5f)
        val area = Area("a", AreaType.CITY, Restriction.WHITE, position = point)
        assertFailsWith<IllegalArgumentException> {
            Tile("X", listOf(area), listOf(Connector("a", to = "b", kind = ConnectorKind.TRIPLE)))
        }
        assertFailsWith<IllegalArgumentException> { Connector("a", to = "b", kind = ConnectorKind.SINGLE) }
        assertFailsWith<IllegalArgumentException> { Connector("a", kind = ConnectorKind.TRIPLE) }
    }

    @Test
    fun `area type keys match their serial names`() {
        for (type in AreaType.entries) {
            assertEquals("\"${type.key}\"", Json.encodeToString(AreaType.serializer(), type))
        }
    }

    @Test
    fun `a system needs one entry event per die face`() {
        val planet = Planet(1, listOf("J", "K"), 3, Air.GOOD)
        assertFailsWith<IllegalArgumentException> { StarSystem("s", RuleRef("r207a"), 0, 30, listOf(EventRef("e090")), listOf(planet)) }
        val events = (1..6).map { EventRef("e09$it") }
        assertEquals(EventRef("e096"), StarSystem("s", RuleRef("r207a"), 0, 30, events, listOf(planet)).entryEvent(6))
    }
}
