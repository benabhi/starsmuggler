package com.starsmuggler.engine.content

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** A rule paragraph of the rule book, such as `r207a` or `r233`. Its text lives in the text assets. */
@Serializable
@JvmInline
value class RuleRef(val id: String) {
    init {
        require(PATTERN.matches(id)) { "Not a rule id: '$id' (expected r### or r###a)" }
    }

    override fun toString(): String = id

    companion object {
        val PATTERN = Regex("r\\d{3}[a-z]?")
    }
}

/** An event of the event book, such as `e090`. Its text lives in the text assets. */
@Serializable
@JvmInline
value class EventRef(val id: String) {
    init {
        require(PATTERN.matches(id)) { "Not an event id: '$id' (expected e###)" }
    }

    override fun toString(): String = id

    companion object {
        val PATTERN = Regex("e\\d{3}")
    }
}

/** A roll of [count] six-sided dice, written `1d6` or `2d6` in the data. */
@Serializable(with = DiceSerializer::class)
data class Dice(val count: Int) {
    init {
        require(count in 1..3) { "Unsupported dice count: $count" }
    }

    val min: Int get() = count
    val max: Int get() = count * 6

    override fun toString(): String = "${count}d6"

    companion object {
        private val PATTERN = Regex("([1-3])d6")

        fun parse(text: String): Dice {
            val match = PATTERN.matchEntire(text) ?: throw IllegalArgumentException("Not a dice roll: '$text' (expected 1d6, 2d6 or 3d6)")
            return Dice(match.groupValues[1].toInt())
        }
    }
}

/**
 * The modified roll results a table entry covers, written `3`, `1-2` or `6+` in the data:
 * `6+` also takes any higher result, as the tables do after modifiers.
 */
@Serializable(with = RollRangeSerializer::class)
data class RollRange(val min: Int, val max: Int) {
    init {
        require(min <= max) { "Empty roll range $min-$max" }
    }

    operator fun contains(roll: Int): Boolean = roll in min..max

    val isOpenEnded: Boolean get() = max == Int.MAX_VALUE

    override fun toString(): String = when {
        isOpenEnded -> "$min+"
        min == max -> "$min"
        else -> "$min-$max"
    }

    companion object {
        private val PATTERN = Regex("(-?\\d+)(?:(\\+)|-(\\d+))?")

        fun parse(text: String): RollRange {
            val match = PATTERN.matchEntire(text) ?: throw IllegalArgumentException("Not a roll range: '$text' (expected 3, 1-2 or 6+)")
            val (min, plus, max) = match.destructured
            val low = min.toInt()
            return when {
                plus.isNotEmpty() -> RollRange(low, Int.MAX_VALUE)
                max.isNotEmpty() -> RollRange(low, max.toInt())
                else -> RollRange(low, low)
            }
        }
    }
}

/** Reads and writes a value as the plain string of [parse] and [toString]. */
internal abstract class StringFormSerializer<T>(name: String) : KSerializer<T> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(name, PrimitiveKind.STRING)

    abstract fun parse(text: String): T

    override fun deserialize(decoder: Decoder): T {
        val text = decoder.decodeString()
        try {
            return parse(text)
        } catch (e: IllegalArgumentException) {
            throw SerializationException(e.message, e)
        }
    }

    override fun serialize(encoder: Encoder, value: T) = encoder.encodeString(value.toString())
}

internal object DiceSerializer : StringFormSerializer<Dice>("com.starsmuggler.Dice") {
    override fun parse(text: String) = Dice.parse(text)
}

internal object RollRangeSerializer : StringFormSerializer<RollRange>("com.starsmuggler.RollRange") {
    override fun parse(text: String) = RollRange.parse(text)
}
