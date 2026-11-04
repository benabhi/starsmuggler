package com.starsmuggler.engine

import com.starsmuggler.engine.EngineTest.Companion.content
import com.starsmuggler.engine.EngineTest.Companion.runSync
import com.starsmuggler.engine.EngineTest.Companion.start
import com.starsmuggler.engine.content.EventRef
import com.starsmuggler.engine.content.RuleRef
import com.starsmuggler.engine.dice.DiceHand
import com.starsmuggler.engine.dice.RollRequest
import com.starsmuggler.engine.dice.RollSource
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DukeTest {
    private val engine = Engine(content())

    @Test
    fun `Duke starts as r201b and e001 describe him`() {
        val game = engine.start(1)
        val duke = game.duke
        assertEquals(5, duke.marksmanship)
        assertEquals(6, duke.melee)
        assertEquals(10, duke.endurance)
        assertEquals(listOf(Skill.PILOT_NAVIGATOR, Skill.STARSHIP_GUNNER), duke.skills)
        assertEquals(listOf(Item("utility_suit", 1), Item("small_arms", 1)), duke.items)
        assertEquals(Ship(tech = 1, hypercharges = 6, guns = 1, stasis = 2, boat = Boat(tech = 1, fuel = 15)), game.ship)
        assertEquals(Location("regari", 1, "K", "spaceport"), game.location)
        assertEquals(Standing(papersInOrder = true, wantedIn = emptyList()), game.standing)
        assertEquals(Clock(1, 0), game.clock)
    }

    @Test
    fun `a first game rolls Astucia and the cash`() {
        val asked = mutableListOf<RollRequest>()
        val faces = ArrayDeque(listOf(4, 3))
        val game = runSync { engine.newGame(1, hand = DiceHand.physical { asked += it; listOf(faces.removeFirst()) }) }
        assertEquals(4, game.duke.astucia)
        assertEquals(450, game.cash, "3 x 100 + 150, the example of e001")
        assertEquals(listOf(RuleRef("r202"), RuleRef("r232a")), asked.map { it.rule })
        assertEquals(EventRef("e001"), asked[1].event)
        val entries = game.log.drop(1)
        assertEquals(RollSource.PHYSICAL, assertIs<LogEntry.Rolled>(entries[0]).roll.source)
        assertEquals(LogEntry.AstuciaSet(0, 4), entries[1])
        assertEquals(LogEntry.StartingCash(0, 450), entries[3])
    }

    @Test
    fun `starting cash goes from 250 to 750 Sees`() {
        val cash = (1L..200L).map { engine.start(it).cash }.toSet()
        assertEquals(setOf(250, 350, 450, 550, 650, 750), cash)
        val astucia = (1L..200L).map { engine.start(it).duke.astucia }.toSet()
        assertEquals((1..6).toSet(), astucia)
    }

    @Test
    fun `a later game adjusts the previous Astucia without rolling it`() {
        val faces = mutableListOf<Int>()
        val hand = DiceHand.physical { faces += 2; listOf(2) }
        val game = runSync { engine.newGame(1, AstuciaSetup.Adjust(previous = 3, adjustment = -1), hand) }
        assertEquals(2, game.duke.astucia)
        assertEquals(1, faces.size, "Only the cash is rolled")
        assertEquals(LogEntry.AstuciaSet(0, 2, previous = 3, adjustment = -1), game.log[1])
    }

    @Test
    fun `Astucia stays within 0 and 6`() {
        assertEquals(6, Astucia.adjusted(6, 1))
        assertEquals(0, Astucia.adjusted(0, -1))
        assertEquals(1, Astucia.suggestion(won = false))
        assertEquals(-1, Astucia.suggestion(won = true))
        assertFailsWith<IllegalArgumentException> { AstuciaSetup.Adjust(3, 2) }
        assertFailsWith<IllegalArgumentException> { AstuciaSetup.Adjust(7, 0) }
        assertFailsWith<IllegalArgumentException> { Duke(astucia = 7) }
    }

    @Test
    fun `an Astucia roll succeeds when not higher than Astucia`() {
        val game = runSync { engine.newGame(1, AstuciaSetup.Adjust(3, 0)) }
        for ((face, success) in listOf(1 to true, 3 to true, 4 to false, 6 to false)) {
            val done = assertIs<ActionResult.Done>(runSync { engine.perform(game, Action.AstuciaRoll(), DiceHand.physical { listOf(face) }) })
            assertEquals(LogEntry.AstuciaRolled(1, 3, face, success), done.entries.last())
        }
        val modified = assertIs<ActionResult.Done>(runSync { engine.perform(game, Action.AstuciaRoll(modifier = 1), DiceHand.physical { listOf(3) }) })
        assertEquals(false, assertIs<LogEntry.AstuciaRolled>(modified.entries.last()).success)
    }

    @Test
    fun `Duke and his ship are saved with the game`() {
        val game = engine.start(9)
        assertEquals(game, Json.decodeFromString<GameState>(Json.encodeToString(game)))
        assertTrue(game.log.any { it is LogEntry.StartingCash })
    }
}
