package com.starsmuggler.engine

import com.starsmuggler.engine.content.Outcome
import com.starsmuggler.engine.dice.DiceRoll
import com.starsmuggler.engine.dice.Rng
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A game at one moment. It is never changed: each action makes a new state. It holds ids and
 * values only, never text, and is serializable so it can be saved as it is.
 */
@Serializable
data class GameState(
    /** The [Engine.RULES_VERSION] the game was played with. */
    val rulesVersion: Int,
    /** The seed the game started with. */
    val seed: Long,
    /** The generator after the last die the engine rolled. */
    val rng: Rng,
    /** How many actions have been performed. */
    val step: Int,
    /** The game's history, oldest first (PRD section 7, log). */
    val log: List<LogEntry>,
)

/** One line of the game's history, written by the action of number [step]. The interface turns it into text. */
@Serializable
sealed interface LogEntry {
    val step: Int

    @Serializable
    @SerialName("started")
    data class GameStarted(override val step: Int, val seed: Long, val rulesVersion: Int) : LogEntry

    /** A roll of the dice, made by the app or by the player. */
    @Serializable
    @SerialName("rolled")
    data class Rolled(override val step: Int, val roll: DiceRoll) : LogEntry

    /** The result of a modified roll [total] on [table], in its row [key]; null when no entry covers the roll. */
    @Serializable
    @SerialName("table")
    data class TableResult(
        override val step: Int,
        val table: String,
        val key: String? = null,
        val total: Int,
        val outcome: Outcome?,
    ) : LogEntry
}
