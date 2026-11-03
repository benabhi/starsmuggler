package com.starsmuggler.engine.content

import kotlinx.serialization.Serializable

/**
 * What a table entry sends the player to, written as one string in the data:
 * `e129` (event), `r337` (rule paragraph), `SIST` (roll on the system table, r233),
 * `stop` (no more rolls, r205) or a number (a value, such as a tech level in r235).
 */
@Serializable(with = OutcomeSerializer::class)
sealed interface Outcome {
    data class Event(val ref: EventRef) : Outcome {
        override fun toString() = ref.id
    }

    data class Rule(val ref: RuleRef) : Outcome {
        override fun toString() = ref.id
    }

    data object SystemTable : Outcome {
        override fun toString() = SYSTEM_TABLE
    }

    data object NoMoreRolls : Outcome {
        override fun toString() = NO_MORE_ROLLS
    }

    data class Value(val value: Int) : Outcome {
        override fun toString() = value.toString()
    }

    companion object {
        const val SYSTEM_TABLE = "SIST"
        const val NO_MORE_ROLLS = "stop"

        fun parse(text: String): Outcome = when {
            text == SYSTEM_TABLE -> SystemTable
            text == NO_MORE_ROLLS -> NoMoreRolls
            EventRef.PATTERN.matches(text) -> Event(EventRef(text))
            RuleRef.PATTERN.matches(text) -> Rule(RuleRef(text))
            text.toIntOrNull() != null -> Value(text.toInt())
            else -> throw IllegalArgumentException("Not a table result: '$text' (expected e###, r###, SIST, stop or a number)")
        }
    }
}

internal object OutcomeSerializer : StringFormSerializer<Outcome>("com.starsmuggler.Outcome") {
    override fun parse(text: String) = Outcome.parse(text)
}
