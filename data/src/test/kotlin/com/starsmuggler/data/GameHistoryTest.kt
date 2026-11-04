package com.starsmuggler.data

import com.starsmuggler.engine.AstuciaSetup
import com.starsmuggler.engine.Clock
import com.starsmuggler.engine.Duke
import com.starsmuggler.engine.Ending
import com.starsmuggler.engine.Engine
import com.starsmuggler.engine.GameState
import com.starsmuggler.engine.Loan
import com.starsmuggler.engine.LogEntry
import com.starsmuggler.engine.dice.Rng
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GameHistoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private var time = 1_000L
    private val history: GameHistory get() = GameHistory(folder.root) { time }

    @Test
    fun `no games and no suggestion at first`() {
        assertEquals(emptyList(), history.games())
        assertEquals(null, history.suggestion())
    }

    @Test
    fun `a finished game is recorded with what P10 asks for`() {
        val record = history.add(ended(Ending.RETIRED, cash = 900, debt = 120_000, astucia = 4, day = 37))
        assertEquals(GameRecord(1_000L, Ending.RETIRED, won = false, days = 37, cash = 900, debt = 120_000, astucia = 4, seed = 7), record)
        assertEquals(listOf(record), GameHistory(folder.root).games())
        assertEquals(-119_100, record.netWorth)
    }

    @Test
    fun `games pile up oldest first`() {
        history.add(ended(Ending.DIED))
        time = 2_000L
        history.add(ended(Ending.RETIRED, debt = 0, cash = 50_000))
        assertEquals(listOf(Ending.DIED, Ending.RETIRED), history.games().map { it.ending })
        assertTrue(history.games().last().won)
    }

    @Test
    fun `the next Astucia follows the last game`() {
        history.add(ended(Ending.DIED, astucia = 3))
        assertEquals(AstuciaSuggestion(previous = 3, adjustment = 1, lastWon = false), history.suggestion())
        history.add(ended(Ending.RETIRED, debt = 0, astucia = 5))
        val won = history.suggestion()!!
        assertEquals(AstuciaSuggestion(previous = 5, adjustment = -1, lastWon = true), won)
        assertEquals(AstuciaSetup.Adjust(5, -1), won.accept)
        assertEquals(AstuciaSetup.Adjust(5, 1), won.invert)
        assertEquals(AstuciaSetup.Adjust(5, 0), won.keep)
    }

    @Test
    fun `a broken history is moved aside, not overwritten`() {
        File(folder.root, GameHistory.FILE).writeText("{ broken")
        assertEquals(emptyList(), history.games())
        assertTrue(folder.root.list()!!.any { it.startsWith("history.json.damaged-") })
        history.add(ended(Ending.RETIRED))
        assertEquals(1, history.games().size)
    }

    @Test
    fun `only ended games can be recorded`() {
        assertFailsWith<IllegalStateException> { history.add(ended(Ending.RETIRED).copy(ending = null)) }
    }

    private fun ended(ending: Ending, cash: Int = 500, debt: Int = Loan.PRINCIPAL, astucia: Int = 3, day: Int = 12) = GameState(
        rulesVersion = Engine.RULES_VERSION,
        seed = 7,
        rng = Rng(7),
        step = 3,
        clock = Clock(day),
        duke = Duke(astucia = astucia),
        cash = cash,
        loan = Loan(principal = debt),
        ending = ending,
        log = listOf(LogEntry.GameStarted(0, 7, Engine.RULES_VERSION)),
    )
}
