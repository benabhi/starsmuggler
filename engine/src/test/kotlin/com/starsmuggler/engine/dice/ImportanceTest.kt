package com.starsmuggler.engine.dice

import com.starsmuggler.engine.content.Dice
import com.starsmuggler.engine.content.EventRef
import com.starsmuggler.engine.content.RuleRef
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ImportanceTest {
    private fun request(rule: String, table: String? = null, event: String? = null) =
        RollRequest(Dice(1), RuleRef(rule), table, event = event?.let(::EventRef))

    @Test
    fun `combat, jumps, contacts and Astucia are important`() {
        assertTrue(request("r202").important)
        assertTrue(request("r230d").important)
        assertTrue(request("r212d").important)
        assertTrue(request("r227").important)
        assertTrue(request("r233", table = "r233").important)
    }

    @Test
    fun `any roll an event asks for is important`() {
        assertTrue(request("r232a", event = "e001").important)
    }

    @Test
    fun `routine checks are not`() {
        assertFalse(request("r218a").important)
        assertFalse(request("r232a").important)
    }
}
