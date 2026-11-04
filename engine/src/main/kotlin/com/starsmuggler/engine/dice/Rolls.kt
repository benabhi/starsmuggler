package com.starsmuggler.engine.dice

import com.starsmuggler.engine.content.Dice
import com.starsmuggler.engine.content.EventRef
import com.starsmuggler.engine.content.RuleRef
import kotlinx.serialization.Serializable

/** A roll the rules ask for: how many dice, which rule or table asks, and the modifier it adds. */
@Serializable
data class RollRequest(
    val dice: Dice,
    val rule: RuleRef,
    /** The table rolled on, such as `r233`, when the roll is a table lookup. */
    val table: String? = null,
    val modifier: Int = 0,
    /** The event that asks for the roll, such as `e001`. */
    val event: EventRef? = null,
)

/** Who rolled a die: the app, or the player with their own dice on the table (PRD section 11). */
@Serializable
enum class RollSource { APP, PHYSICAL }

/** A finished roll: the face of each die, in order, and who rolled them. */
@Serializable
data class DiceRoll(
    val request: RollRequest,
    val values: List<Int>,
    val source: RollSource,
) {
    init {
        require(values.size == request.dice.count) { "A ${request.dice} roll needs ${request.dice.count} values, got ${values.size}" }
        require(values.all { it in 1..Rng.FACES }) { "Die faces go from 1 to ${Rng.FACES}, got $values" }
    }

    /** The sum of the dice before the modifier. */
    val natural: Int get() = values.sum()

    /** The modified result the rules look up. */
    val total: Int get() = natural + request.modifier
}

/**
 * Where the dice of an action come from. By default the engine rolls them with the game's
 * generator; with the player's own dice (PRD section 11) [physical] returns the faces the player
 * entered. Both are suspending, so the interface can wait for the player or for the 3D dice.
 */
interface DiceHand {
    /** The faces the player rolled for [request], or null to let the engine roll them. */
    suspend fun physical(request: RollRequest): List<Int>? = null

    /** Called with every roll before the action goes on, such as to animate it. */
    suspend fun show(roll: DiceRoll) {}

    companion object {
        /** Every die rolled by the engine, with nothing to wait for. */
        val APP: DiceHand = object : DiceHand {}

        /** The player rolls every die; [read] asks for the faces. */
        fun physical(read: suspend (RollRequest) -> List<Int>): DiceHand = object : DiceHand {
            override suspend fun physical(request: RollRequest): List<Int> = read(request)
        }
    }
}
