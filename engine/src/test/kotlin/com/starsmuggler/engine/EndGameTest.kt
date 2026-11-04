package com.starsmuggler.engine

import com.starsmuggler.engine.EngineTest.Companion.content
import com.starsmuggler.engine.EngineTest.Companion.runSync
import com.starsmuggler.engine.EngineTest.Companion.start
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class EndGameTest {
    private val engine = Engine(content())

    @Test
    fun `retiring with the loan still owed is not a win`() {
        val game = play(play(engine.start(1), Action.EndDay), Action.EndGame(Ending.RETIRED))
        assertEquals(Ending.RETIRED, game.ending)
        assertFalse(game.won)
        val ended = game.log.last()
        assertEquals(LogEntry.GameEnded(2, Ending.RETIRED, won = false, day = 2, cash = game.cash, debt = 120_000), ended)
    }

    @Test
    fun `retiring with the loan paid off wins`() {
        val rich = play(engine.start(1), Action.AdjustCash(200_000))
        val paid = play(rich, Action.PayPrincipal(120_000))
        val game = play(paid, Action.EndGame(Ending.RETIRED))
        assertTrue(game.won)
        assertTrue(assertIs<LogEntry.GameEnded>(game.log.last()).won)
    }

    @Test
    fun `death is a defeat even with the loan paid`() {
        val paid = play(play(engine.start(1), Action.AdjustCash(200_000)), Action.PayPrincipal(120_000))
        val dead = play(paid, Action.EndGame(Ending.DIED))
        assertFalse(dead.won)
    }

    @Test
    fun `nothing can be done once the game is over`() {
        val over = play(engine.start(1), Action.EndGame(Ending.RETIRED))
        for (action in listOf(Action.EndDay, Action.AstuciaRoll(), Action.EndGame(Ending.DIED), Action.AdjustCash(10))) {
            assertEquals(Rejection.GAME_OVER, engine.check(over, action))
            assertEquals(ActionResult.Rejected(Rejection.GAME_OVER), runSync { engine.perform(over, action) })
        }
    }

    @Test
    fun `an ended game is saved as ended`() {
        val over = play(engine.start(1), Action.EndGame(Ending.DIED))
        assertEquals(over, Json.decodeFromString<GameState>(Json.encodeToString(over)))
    }

    private fun play(state: GameState, action: Action): GameState =
        assertIs<ActionResult.Done>(runSync { engine.perform(state, action) }).state
}
