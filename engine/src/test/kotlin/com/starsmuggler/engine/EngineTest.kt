package com.starsmuggler.engine

import kotlin.test.Test
import kotlin.test.assertEquals

class EngineTest {
    @Test
    fun `rules model starts at version 1`() {
        assertEquals(1, Engine.RULES_VERSION)
    }
}
