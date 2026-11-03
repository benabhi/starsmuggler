package com.starsmuggler.engine

import com.starsmuggler.engine.content.DiceTable
import com.starsmuggler.engine.content.GameContent
import com.starsmuggler.engine.content.Outcome
import com.starsmuggler.engine.content.RuleRef
import com.starsmuggler.engine.content.Sector
import com.starsmuggler.engine.dice.DiceHand
import com.starsmuggler.engine.dice.DiceRoll
import com.starsmuggler.engine.dice.Rng
import com.starsmuggler.engine.dice.RollRequest
import com.starsmuggler.engine.dice.RollSource
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class EngineTest {
    private val engine = Engine(content())

    @Test
    fun `rules model starts at version 1`() {
        assertEquals(1, Engine.RULES_VERSION)
    }

    @Test
    fun `a new game starts at step 0 with its seed in the log`() {
        val game = engine.newGame(42)
        assertEquals(0, game.step)
        assertEquals(42, game.seed)
        assertEquals(listOf<LogEntry>(LogEntry.GameStarted(0, 42, Engine.RULES_VERSION)), game.log)
    }

    @Test
    fun `the same seed and actions roll the same dice`() {
        val a = play(engine.newGame(7), 20)
        val b = play(engine.newGame(7), 20)
        assertEquals(a, b)
        assertNotEquals(a.log, play(engine.newGame(8), 20).log)
    }

    @Test
    fun `a saved game goes on with the same dice`() {
        val start = play(engine.newGame(99), 5)
        val loaded = Json.decodeFromString<GameState>(Json.encodeToString(start))
        assertEquals(start, loaded)
        assertEquals(play(start, 10), play(loaded, 10))
    }

    @Test
    fun `an action builds a new state and logs its roll and result`() {
        val start = engine.newGame(3)
        val done = assertIs<ActionResult.Done>(perform(start, Action.RollOnTable("r233", key = "city", modifier = 1)))
        assertEquals(1, done.state.step)
        assertEquals(start.log + done.entries, done.state.log)
        assertEquals(0, start.step, "The old state is never changed")
        val rolled = assertIs<LogEntry.Rolled>(done.entries[0])
        val result = assertIs<LogEntry.TableResult>(done.entries[1])
        assertEquals(RollSource.APP, rolled.roll.source)
        assertEquals(rolled.roll.natural + 1, result.total)
        assertEquals(TABLE.lookup(result.total, "city"), result.outcome)
        assertNotEquals(start.rng, done.state.rng)
    }

    @Test
    fun `app dice are fair and stay within 1 to 6`() {
        val counts = IntArray(7)
        var rng = Rng(1)
        repeat(60_000) {
            val (face, next) = rng.nextFace()
            counts[face]++
            rng = next
        }
        assertEquals(0, counts[0])
        for (face in 1..6) assertTrue(counts[face] in 9_500..10_500, "Face $face came up ${counts[face]} times")
    }

    @Test
    fun `physical dice come from outside and leave the generator alone`() {
        val start = engine.newGame(5)
        val asked = mutableListOf<RollRequest>()
        val hand = DiceHand.physical { request -> asked += request; listOf(6) }
        val done = assertIs<ActionResult.Done>(perform(start, Action.RollOnTable("r233", key = "city"), hand))
        val roll = assertIs<LogEntry.Rolled>(done.entries[0]).roll
        assertEquals(listOf(6), roll.values)
        assertEquals(RollSource.PHYSICAL, roll.source)
        assertEquals(listOf(RollRequest(TABLE.dice!!, TABLE.rule, TABLE.id)), asked)
        assertEquals(start.rng, done.state.rng)
        assertEquals(Outcome.SystemTable, assertIs<LogEntry.TableResult>(done.entries[1]).outcome)
    }

    @Test
    fun `physical dice must match the roll`() {
        val start = engine.newGame(5)
        assertFailsWith<IllegalArgumentException> { perform(start, Action.RollOnTable("r233", "city"), DiceHand.physical { listOf(7) }) }
        assertFailsWith<IllegalArgumentException> { perform(start, Action.RollOnTable("r233", "city"), DiceHand.physical { listOf(2, 3) }) }
    }

    @Test
    fun `every roll is shown before the action goes on`() {
        val shown = mutableListOf<DiceRoll>()
        val hand = object : DiceHand {
            override suspend fun show(roll: DiceRoll) {
                shown += roll
            }
        }
        val done = assertIs<ActionResult.Done>(perform(engine.newGame(1), Action.RollOnTable("r233", "city"), hand))
        assertEquals(listOf(assertIs<LogEntry.Rolled>(done.entries[0]).roll), shown)
    }

    @Test
    fun `impossible actions are rejected without rolling`() {
        val start = engine.newGame(1)
        val never = DiceHand.physical { error("No dice should be asked for") }
        for ((action, reason) in listOf(
            Action.RollOnTable("r999") to Rejection.UNKNOWN_TABLE,
            Action.RollOnTable("r238") to Rejection.TABLE_WITHOUT_DICE,
            Action.RollOnTable("r233", key = "moon") to Rejection.UNKNOWN_ROW,
        )) {
            assertEquals(reason, engine.check(start, action))
            assertEquals(ActionResult.Rejected(reason), perform(start, action, never))
        }
        assertEquals(null, engine.check(start, Action.RollOnTable("r233", "city")))
    }

    @Test
    fun `a game from other rules is refused`() {
        val old = engine.newGame(1).copy(rulesVersion = 0)
        assertFailsWith<IllegalArgumentException> { perform(old, Action.RollOnTable("r233", "city")) }
    }

    @Test
    fun `a roll outside the table logs no outcome`() {
        val done = assertIs<ActionResult.Done>(perform(engine.newGame(1), Action.RollOnTable("r233", "city", modifier = -20)))
        assertEquals(null, assertIs<LogEntry.TableResult>(done.entries[1]).outcome)
    }

    @Test
    fun `log entries keep ids and values only`() {
        val state = play(engine.newGame(11), 2)
        val json = Json.encodeToString(state)
        assertTrue("\"type\":\"rolled\"" in json && "\"outcome\":\"" in json, json)
    }

    private fun play(state: GameState, actions: Int): GameState {
        var game = state
        repeat(actions) { game = assertIs<ActionResult.Done>(perform(game, Action.RollOnTable("r233", "city"))).state }
        return game
    }

    private fun perform(state: GameState, action: Action, hand: DiceHand = DiceHand.APP): ActionResult = runSync { engine.perform(state, action, hand) }

    companion object {
        private val TABLE = Json.decodeFromString<DiceTable>(
            """{"id":"r233","rule":"r233","dice":"1d6","rows":[{"key":"city","entries":[
               {"roll":"1","result":"e129"},{"roll":"2-5","result":"stop"},{"roll":"6","result":"SIST"}]}]}""",
        )
        private val LOOKED_UP = Json.decodeFromString<DiceTable>(
            """{"id":"r238","rule":"r238","rows":[{"entries":[{"roll":"0-50","result":"1"},{"roll":"51+","result":"2"}]}]}""",
        )

        fun content() = GameContent(
            sector = Sector("test", RuleRef("r207"), systems = emptyList(), patterns = emptyList()),
            tiles = emptyList(),
            tables = listOf(TABLE, LOOKED_UP),
            factors = emptyList(),
            paragraphs = emptyList(),
            equipment = emptyList(),
            goods = emptyList(),
            events = emptyList(),
        )

        /** Runs a suspending call that never really suspends, as the engine does with these hands. */
        fun <T> runSync(block: suspend () -> T): T {
            var result: Result<T>? = null
            block.startCoroutine(Continuation(EmptyCoroutineContext) { result = it })
            return checkNotNull(result) { "The call suspended" }.getOrThrow()
        }
    }
}
