package com.starsmuggler.engine.content

import kotlinx.serialization.Serializable

/**
 * What a table entry gives, written as one string in the data:
 * `e129` (event), `r337` (rule paragraph), `SIST` (roll on the system table, r233),
 * `stop` (no more rolls, r205), `#shields_fail` (a named result the engine applies and the
 * text assets describe), `x1.5` (a price multiplier, r241), or a number, `*1` when the table
 * marks it with its footnote (r235).
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

    data class Key(val key: String) : Outcome {
        init {
            require(KEY.matches(key)) { "Not a result key: '$key' (expected lowercase words joined by _)" }
        }

        override fun toString() = "#$key"
    }

    /** A multiplier in halves, so `x1.5` is exact: [halves] = 3. */
    data class Multiplier(val halves: Int) : Outcome {
        val value: Double get() = halves / 2.0

        override fun toString() = if (halves % 2 == 0) "x${halves / 2}" else "x${halves / 2}.5"
    }

    data class Value(val value: Int, val marked: Boolean = false) : Outcome {
        override fun toString() = if (marked) "*$value" else value.toString()
    }

    companion object {
        const val SYSTEM_TABLE = "SIST"
        const val NO_MORE_ROLLS = "stop"
        private val KEY = Regex("[a-z][a-z0-9_]*")
        private val MULTIPLIER = Regex("x(\\d+)(\\.5)?")
        private val VALUE = Regex("(\\*)?(-?\\d+)")

        fun parse(text: String): Outcome {
            MULTIPLIER.matchEntire(text)?.let { m ->
                return Multiplier(m.groupValues[1].toInt() * 2 + if (m.groupValues[2].isEmpty()) 0 else 1)
            }
            VALUE.matchEntire(text)?.let { m -> return Value(m.groupValues[2].toInt(), marked = m.groupValues[1].isNotEmpty()) }
            return when {
                text == SYSTEM_TABLE -> SystemTable
                text == NO_MORE_ROLLS -> NoMoreRolls
                text.startsWith("#") -> Key(text.substring(1))
                EventRef.PATTERN.matches(text) -> Event(EventRef(text))
                RuleRef.PATTERN.matches(text) -> Rule(RuleRef(text))
                else -> throw IllegalArgumentException("Not a table result: '$text' (expected e###, r###, SIST, stop, #key, x2 or a number)")
            }
        }
    }
}

internal object OutcomeSerializer : StringFormSerializer<Outcome>("com.starsmuggler.Outcome") {
    override fun parse(text: String) = Outcome.parse(text)
}
