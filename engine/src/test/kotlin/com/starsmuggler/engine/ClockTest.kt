package com.starsmuggler.engine

import com.starsmuggler.engine.EngineTest.Companion.content
import com.starsmuggler.engine.EngineTest.Companion.runSync
import com.starsmuggler.engine.EngineTest.Companion.start
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ClockTest {
    private val engine = Engine(content())

    @Test
    fun `a game starts on week 1, day 1, with ten hours`() {
        val clock = engine.start(1).clock
        assertEquals(1, clock.week)
        assertEquals(1, clock.dayOfWeek)
        assertEquals(100, clock.left)
        assertTrue(clock.isFresh)
    }

    @Test
    fun `weeks have ten days`() {
        assertEquals(1, Clock(10).week)
        assertEquals(10, Clock(10).dayOfWeek)
        assertTrue(Clock(10).endsWeek)
        assertEquals(2, Clock(11).week)
        assertEquals(1, Clock(11).dayOfWeek)
        assertFalse(Clock(11).endsWeek)
        assertEquals(3, Clock(30).week)
    }

    @Test
    fun `time counts in tenths of an hour`() {
        val clock = Clock().spend(Clock.HOUR * 6 + 5)
        assertEquals(35, clock.left)
        assertEquals(0, clock.overtime)
        assertFalse(clock.isFresh)
        assertTrue(clock.fits(35))
        assertFalse(clock.fits(36))
        assertFailsWith<IllegalArgumentException> { clock.spend(-1) }
    }

    @Test
    fun `an event past the ten hours takes overtime the same day`() {
        val start = play(engine.start(1), Action.PassTime(80, event = false))
        val done = done(start, Action.PassTime(35))
        assertEquals(1, done.state.clock.day)
        assertEquals(15, done.state.clock.overtime)
        assertEquals(0, done.state.clock.left)
        assertEquals(listOf<LogEntry>(LogEntry.TimePassed(2, 35, overtime = 15)), done.entries)
    }

    @Test
    fun `a player action must fit in the hours left`() {
        val late = play(engine.start(1), Action.PassTime(95))
        assertEquals(Rejection.NOT_ENOUGH_HOURS, engine.check(late, Action.PassTime(10, event = false)))
        assertEquals(ActionResult.Rejected(Rejection.NOT_ENOUGH_HOURS), perform(late, Action.PassTime(10, event = false)))
        assertEquals(null, engine.check(late, Action.PassTime(5, event = false)))
        assertEquals(Rejection.NO_TIME, engine.check(late, Action.PassTime(0)))
    }

    @Test
    fun `the rest of the day uses up the hours left`() {
        val start = play(engine.start(1), Action.PassTime(32))
        val done = done(start, Action.PassRestOfDay)
        assertEquals(0, done.state.clock.left)
        assertEquals(0, done.state.clock.overtime)
        assertEquals(listOf<LogEntry>(LogEntry.TimePassed(2, 68)), done.entries)
        assertEquals(Rejection.NO_TIME, engine.check(done.state, Action.PassRestOfDay))
    }

    @Test
    fun `ending the day opens the next morning`() {
        val start = play(engine.start(1), Action.PassTime(120))
        val done = done(start, Action.EndDay)
        assertEquals(Clock(2), done.state.clock)
        assertEquals(listOf<LogEntry>(LogEntry.DayEnded(2, day = 1, overtime = 20)), done.entries)
    }

    @Test
    fun `the tenth day closes the week`() {
        var game = engine.start(1)
        repeat(9) { game = play(game, Action.EndDay) }
        assertEquals(10, game.clock.day)
        val done = done(game, Action.EndDay)
        assertEquals(listOf(LogEntry.DayEnded(10, 10), LogEntry.WeekEnded(10, 1)), done.entries)
        assertEquals(2, done.state.clock.week)
        assertEquals(1, done.state.clock.dayOfWeek)
        game = done.state
        repeat(10) { game = play(game, Action.EndDay) }
        assertEquals(2, game.log.count { it is LogEntry.WeekEnded })
    }

    @Test
    fun `the clock is saved with the game`() {
        val game = play(play(engine.start(4), Action.EndDay), Action.PassTime(47))
        val json = Json.encodeToString(game)
        assertEquals(game, Json.decodeFromString<GameState>(json))
        assertEquals(Clock(2, 47), game.clock)
    }

    private fun play(state: GameState, action: Action): GameState = done(state, action).state

    private fun done(state: GameState, action: Action): ActionResult.Done = assertIs<ActionResult.Done>(perform(state, action))

    private fun perform(state: GameState, action: Action): ActionResult = runSync { engine.perform(state, action) }
}
