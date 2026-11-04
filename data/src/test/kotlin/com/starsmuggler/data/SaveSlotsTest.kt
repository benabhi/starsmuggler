package com.starsmuggler.data

import com.starsmuggler.engine.Clock
import com.starsmuggler.engine.Duke
import com.starsmuggler.engine.Engine
import com.starsmuggler.engine.GameState
import com.starsmuggler.engine.LogEntry
import com.starsmuggler.engine.content.Dice
import com.starsmuggler.engine.content.Outcome
import com.starsmuggler.engine.content.RuleRef
import com.starsmuggler.engine.dice.DiceRoll
import com.starsmuggler.engine.dice.RollRequest
import com.starsmuggler.engine.dice.RollSource
import com.starsmuggler.engine.dice.Rng
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SaveSlotsTest {
    @get:Rule
    val folder = TemporaryFolder()

    private var time = 1_000L
    private val dir: File get() = File(folder.root, "saves")
    private val slots: SaveSlots get() = SaveSlots(dir) { time }

    @Test
    fun `three empty slots at first`() {
        assertEquals((1..3).map { SlotInfo.Empty(it) }, slots.list())
        assertEquals(null, slots.latest())
        assertFailsWith<SaveException> { slots.load(1) }
        assertFailsWith<IllegalArgumentException> { slots.save(4, game()) }
    }

    @Test
    fun `a saved game loads back the same`() {
        val game = game(cash = 750, day = 12)
        slots.save(2, game)
        assertEquals(game, SaveSlots(dir).load(2))
        assertEquals(SlotInfo.Saved(2, 1_000L, game), slots.info(2))
        assertTrue(File(dir, "slot-2.json").exists())
        assertTrue(dir.listFiles()!!.none { it.name.endsWith(".tmp") }, "No temporary file left behind")
    }

    @Test
    fun `saving again overwrites the slot, with no older version kept`() {
        slots.save(1, game(cash = 100))
        time = 2_000L
        slots.save(1, game(cash = 200))
        assertEquals(200, slots.load(1).cash)
        assertEquals(listOf("slot-1.json"), dir.list()!!.toList())
    }

    @Test
    fun `continue picks the slot saved last`() {
        slots.save(1, game())
        time = 5_000L
        slots.save(3, game())
        time = 3_000L
        slots.save(2, game())
        assertEquals(3, slots.latest())
    }

    @Test
    fun `a broken file shows as a damaged slot and is kept`() {
        slots.save(1, game())
        dir.mkdirs()
        File(dir, "slot-2.json").writeText("{\"format\":1,\"savedAt\":")
        File(dir, "slot-3.json").writeText(File(dir, "slot-1.json").readText().replace("\"cash\":", "\"money\":"))
        val list = slots.list()
        assertIs<SlotInfo.Saved>(list[0])
        assertIs<SlotInfo.Damaged>(list[1])
        assertIs<SlotInfo.Damaged>(list[2])
        assertTrue(File(dir, "slot-2.json").exists())
        assertEquals(1, slots.latest())
        assertFailsWith<SaveException> { slots.load(2) }
    }

    @Test
    fun `a save from a newer app is not read`() {
        slots.save(1, game())
        val file = File(dir, "slot-1.json")
        file.writeText(file.readText().replaceFirst("\"format\":1", "\"format\":2"))
        val damaged = assertIs<SlotInfo.Damaged>(slots.info(1))
        assertTrue("newer" in damaged.reason, damaged.reason)
    }

    @Test
    fun `every value is written, defaults included`() {
        slots.save(1, game())
        val text = File(dir, "slot-1.json").readText()
        for (key in listOf("\"clock\":", "\"loan\":", "\"ship\":", "\"standing\":", "\"location\":")) assertTrue(key in text, key)
    }

    @Test
    fun `old formats are migrated step by step`() {
        val old = JsonObject(mapOf("format" to JsonPrimitive(1), "name" to JsonPrimitive("a")))
        val steps = mapOf<Int, (JsonObject) -> JsonObject>(
            1 to { JsonObject(it + ("name" to JsonPrimitive("b"))) },
            2 to { JsonObject(it + ("extra" to JsonPrimitive(true))) },
        )
        val migrated = SaveSlots.migrate(old, from = 1, to = 3, steps = steps)
        assertEquals(3, migrated["format"]!!.jsonPrimitive.int)
        assertEquals("b", migrated["name"]!!.jsonPrimitive.content)
        assertTrue("extra" in migrated)
        assertFailsWith<SaveException> { SaveSlots.migrate(old, from = 1, to = 3, steps = emptyMap()) }
    }

    @Test
    fun `a long game stays a reasonable file`() {
        val request = RollRequest(Dice(2), RuleRef("r233"), "r233")
        val log = (1..1500).flatMap { step ->
            listOf(
                LogEntry.Rolled(step, DiceRoll(request, listOf(3, 4), RollSource.APP)),
                LogEntry.TableResult(step, "r233", "city", 7, Outcome.NoMoreRolls),
            )
        }
        val game = game().copy(step = 1500, log = log)
        slots.save(1, game)
        val bytes = File(dir, "slot-1.json").length()
        println("1500 actions, ${log.size} log entries: $bytes bytes")
        assertTrue(bytes < 1_000_000, "$bytes bytes")
        assertEquals(game, slots.load(1))
    }

    @Test
    fun `slots can be deleted`() {
        slots.save(1, game())
        slots.delete(1)
        assertEquals(SlotInfo.Empty(1), slots.info(1))
    }

    private fun game(cash: Int = 500, day: Int = 1) = GameState(
        rulesVersion = Engine.RULES_VERSION,
        seed = 7,
        rng = Rng(7),
        step = 0,
        clock = Clock(day),
        duke = Duke(astucia = 3),
        cash = cash,
        log = listOf(LogEntry.GameStarted(0, 7, Engine.RULES_VERSION)),
    )
}
