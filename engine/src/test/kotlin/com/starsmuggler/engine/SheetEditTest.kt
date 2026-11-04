package com.starsmuggler.engine

import com.starsmuggler.engine.EngineTest.Companion.content
import com.starsmuggler.engine.EngineTest.Companion.runSync
import com.starsmuggler.engine.EngineTest.Companion.start
import com.starsmuggler.engine.content.Equipment
import com.starsmuggler.engine.content.GameContent
import com.starsmuggler.engine.content.RuleRef
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SheetEditTest {
    private val engine = Engine(withEquipment(content(), "repair_unit", "small_arms"))

    @Test
    fun `an edit changes the sheet and is logged`() {
        val start = engine.start(1)
        val done = done(start, SheetEdit.SetStat(Stat.ENDURANCE, 7))
        assertEquals(7, done.state.duke.endurance)
        assertEquals(listOf<LogEntry>(LogEntry.SheetEdited(1, SheetEdit.SetStat(Stat.ENDURANCE, 7))), done.entries)
        assertEquals(10, start.duke.endurance, "The old state is never changed")
    }

    @Test
    fun `every stat keeps to its range`() {
        val game = engine.start(1)
        for ((stat, bad) in listOf(Stat.MARKSMANSHIP to 7, Stat.MELEE to -1, Stat.ENDURANCE to 21, Stat.ASTUCIA to 7)) {
            assertEquals(Rejection.INVALID_VALUE, check(game, SheetEdit.SetStat(stat, bad)), "$stat $bad")
        }
        assertEquals(3, play(game, SheetEdit.SetStat(Stat.ASTUCIA, 3)).duke.astucia)
        assertEquals(0, play(game, SheetEdit.SetStat(Stat.MELEE, 0)).duke.melee)
        assertEquals(4, play(game, SheetEdit.SetStat(Stat.MARKSMANSHIP, 4)).duke.marksmanship)
    }

    @Test
    fun `gear tech and breakdowns can be set by hand`() {
        val game = engine.start(1)
        val raised = play(game, SheetEdit.SetTech(GearRef.StarshipGuns, 4))
        assertEquals(4, raised.ship.guns)
        assertEquals(Rejection.INVALID_VALUE, check(game, SheetEdit.SetTech(GearRef.Starship, 7)))
        assertEquals(Rejection.GEAR_MISSING, check(game, SheetEdit.SetTech(GearRef.DukeItem(5), 2)))
        val broken = play(game, SheetEdit.SetBroken(GearRef.Boat, true))
        assertTrue(broken.ship.boat!!.broken)
        assertFalse(play(broken, SheetEdit.SetBroken(GearRef.Boat, false)).ship.boat!!.broken)
    }

    @Test
    fun `items come and go, and today's use follows them`() {
        val game = engine.start(1)
        val added = play(game, SheetEdit.AddItem("repair_unit", 2))
        assertEquals(Item("repair_unit", 2), added.duke.items.last())
        assertEquals(Rejection.UNKNOWN_ID, check(game, SheetEdit.AddItem("laser_sword", 1)))
        assertEquals(Rejection.INVALID_VALUE, check(game, SheetEdit.AddItem("repair_unit", 0)))
        val used = runSync { engine.perform(runSync { engine.perform(added, Action.UseGear(GearRef.DukeItem(2))) }.let { (it as ActionResult.Done).state }, Action.UseGear(GearRef.DukeItem(0))) }
        val state = assertIs<ActionResult.Done>(used).state
        val removed = play(state, SheetEdit.RemoveItem(1))
        assertEquals(listOf("utility_suit", "repair_unit"), removed.duke.items.map { it.id })
        assertEquals(listOf<GearRef>(GearRef.DukeItem(1), GearRef.DukeItem(0)), removed.usedToday)
        assertEquals(Rejection.GEAR_MISSING, check(removed, SheetEdit.RemoveItem(2)))
    }

    @Test
    fun `ship counts and standing can be edited`() {
        val game = engine.start(1)
        assertEquals(3, play(game, SheetEdit.SetHypercharges(3)).ship.hypercharges)
        assertEquals(0, play(game, SheetEdit.SetBoatFuel(0)).ship.boat!!.fuel)
        assertEquals(Rejection.INVALID_VALUE, check(game, SheetEdit.SetBoatFuel(-1)))
        assertEquals(Rejection.GEAR_MISSING, check(game.copy(ship = game.ship.copy(boat = null)), SheetEdit.SetBoatFuel(5)))
        assertFalse(play(game, SheetEdit.SetPapers(false)).standing.papersInOrder)
    }

    @Test
    fun `wanted systems are a set of known ids`() {
        val game = engine.start(1)
        assertEquals(Rejection.UNKNOWN_ID, check(game, SheetEdit.SetWanted("atlantis", true)))
        val known = { id: String -> id == "regari" || id == "nipna" }
        val wanted = game.edited(SheetEdit.SetWanted("regari", true)).edited(SheetEdit.SetWanted("regari", true)).edited(SheetEdit.SetWanted("nipna", true))
        assertEquals(null, SheetEdit.SetWanted("nipna", true).problem(game, { false }, known))
        assertEquals(listOf("regari", "nipna"), wanted.standing.wantedIn)
        assertEquals(listOf("nipna"), wanted.edited(SheetEdit.SetWanted("regari", false)).standing.wantedIn)
    }

    @Test
    fun `edits are saved in the log`() {
        val game = play(engine.start(1), SheetEdit.AddItem("small_arms", 3))
        assertEquals(game, Json.decodeFromString<GameState>(Json.encodeToString(game)))
    }

    private fun check(state: GameState, edit: SheetEdit) = engine.check(state, Action.EditSheet(edit))

    private fun play(state: GameState, edit: SheetEdit): GameState = done(state, edit).state

    private fun done(state: GameState, edit: SheetEdit): ActionResult.Done =
        assertIs<ActionResult.Done>(runSync { engine.perform(state, Action.EditSheet(edit)) })

    private fun withEquipment(base: GameContent, vararg ids: String) = GameContent(
        sector = base.sector,
        tiles = base.tiles.values.toList(),
        tables = base.tables.values.toList(),
        factors = base.factors.values.toList(),
        paragraphs = base.paragraphs.values.toList(),
        equipment = ids.map { Equipment(it, RuleRef("r236")) },
        goods = base.goods.values.toList(),
        events = base.events.values.toList(),
    )
}
