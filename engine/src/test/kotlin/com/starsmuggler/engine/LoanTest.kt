package com.starsmuggler.engine

import com.starsmuggler.engine.EngineTest.Companion.content
import com.starsmuggler.engine.EngineTest.Companion.runSync
import com.starsmuggler.engine.EngineTest.Companion.start
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LoanTest {
    private val engine = Engine(content())

    @Test
    fun `the game starts owing 120000 Sees with interest due in 10 days`() {
        val game = engine.start(1)
        assertEquals(Loan(principal = 120_000), game.loan)
        assertEquals(10, game.loan.interestDueIn(game.clock))
        assertEquals(1, game.loan.interestDueIn(Clock(10)))
    }

    @Test
    fun `the interest is paid from cash at the end of the week`() {
        val start = withCash(1000)
        val week = endDays(start, 10)
        assertEquals(700, week.cash)
        assertEquals(120_000, week.loan.principal)
        assertFalse(week.loan.isDefaulted)
        assertEquals(LogEntry.InterestPaid(11, 1, 300), week.log.single { it is LogEntry.InterestPaid })
        assertEquals(400, endDays(week, 10).cash)
    }

    @Test
    fun `without the interest the whole principal falls due in 30 days`() {
        val week = endDays(withCash(299), 10)
        assertEquals(299, week.cash)
        assertEquals(10, week.loan.defaultedOn)
        assertEquals(40, week.loan.deadline)
        assertEquals(LogEntry.LoanDefaulted(11, 1, 120_000, deadline = 40), week.log.single { it is LogEntry.LoanDefaulted })
        assertEquals(null, week.loan.interestDueIn(week.clock))
        val later = endDays(week, 10)
        assertEquals(299, later.cash, "No more weekly interest once the whole loan is due")
        assertEquals(1, later.log.count { it is LogEntry.LoanDefaulted })
    }

    @Test
    fun `repossession starts the day after the 30 days`() {
        val defaulted = endDays(withCash(0), 10)
        val lastDay = endDays(defaulted, 29)
        assertEquals(40, lastDay.clock.day)
        assertFalse(lastDay.loan.repossession)
        val done = done(lastDay, Action.EndDay)
        assertTrue(done.state.loan.repossession)
        assertEquals(LogEntry.RepossessionStarted(done.state.step, 41), done.entries.last())
        assertEquals(1, endDays(done.state, 15).log.count { it is LogEntry.RepossessionStarted })
    }

    @Test
    fun `principal can be paid in part and the interest stays the same`() {
        val paid = done(withCash(5000), Action.PayPrincipal(4000))
        assertEquals(1000, paid.state.cash)
        assertEquals(116_000, paid.state.loan.principal)
        assertEquals(listOf<LogEntry>(LogEntry.PrincipalPaid(2, 4000, 116_000)), paid.entries)
        assertEquals(700, endDays(paid.state, 10).cash)
    }

    @Test
    fun `paying the whole principal wins the game and ends a default`() {
        val defaulted = endDays(withCash(0), 10)
        val rich = play(defaulted, Action.AdjustCash(130_000))
        val done = done(rich, Action.PayPrincipal(120_000))
        assertTrue(done.state.loan.isPaidOff)
        assertFalse(done.state.loan.isDefaulted)
        assertEquals(10_000, done.state.cash)
        assertIs<LogEntry.LoanPaidOff>(done.entries.last())
        assertEquals(10_000, endDays(done.state, 40).cash, "No interest once paid off")
        assertFalse(endDays(done.state, 40).loan.repossession)
        assertEquals(Rejection.LOAN_PAID_OFF, engine.check(done.state, Action.PayPrincipal(1)))
    }

    @Test
    fun `payments must be possible`() {
        val game = withCash(500)
        assertEquals(Rejection.NOT_ENOUGH_CASH, engine.check(game, Action.PayPrincipal(501)))
        assertEquals(Rejection.INVALID_AMOUNT, engine.check(game, Action.PayPrincipal(0)))
        assertEquals(Rejection.INVALID_AMOUNT, engine.check(game, Action.PayPrincipal(120_001)))
        assertEquals(Rejection.NOT_ENOUGH_CASH, engine.check(game, Action.AdjustCash(-501)))
        assertEquals(Rejection.INVALID_AMOUNT, engine.check(game, Action.AdjustCash(0)))
        assertEquals(null, engine.check(game, Action.AdjustCash(-500)))
    }

    @Test
    fun `cash adjustments are logged with the new amount`() {
        val done = done(withCash(500), Action.AdjustCash(-200))
        assertEquals(listOf<LogEntry>(LogEntry.CashAdjusted(2, -200, 300)), done.entries)
    }

    /** A new game with exactly [cash] Sees. */
    private fun withCash(cash: Int): GameState {
        val game = engine.start(1)
        return play(game, Action.AdjustCash(cash - game.cash).takeIf { it.sees != 0 } ?: Action.PassTime(1))
    }

    private fun endDays(state: GameState, days: Int): GameState {
        var game = state
        repeat(days) { game = play(game, Action.EndDay) }
        return game
    }

    private fun play(state: GameState, action: Action): GameState = done(state, action).state

    private fun done(state: GameState, action: Action): ActionResult.Done =
        assertIs<ActionResult.Done>(runSync { engine.perform(state, action) })
}
