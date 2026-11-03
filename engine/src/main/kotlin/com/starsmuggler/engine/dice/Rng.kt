package com.starsmuggler.engine.dice

import kotlinx.serialization.Serializable

/**
 * The game's own seeded random generator (SplitMix64). Its whole state is one number kept in the
 * game state, so a saved game goes on with the same sequence after loading, and two games with the
 * same seed and the same choices roll the same dice.
 */
@Serializable
@JvmInline
value class Rng(val state: Long) {
    /** The face of a six-sided die, 1 to 6, and the generator after it. */
    fun nextFace(): Pair<Int, Rng> {
        var next = state
        while (true) {
            next += GAMMA
            // 63 uniform bits; the few highest values are dropped so every face is equally likely.
            val bits = mix(next) ushr 1
            if (bits <= Long.MAX_VALUE - REJECTED) return Pair((bits % FACES).toInt() + 1, Rng(next))
        }
    }

    companion object {
        const val FACES = 6
        private const val GAMMA = -0x61c8864680b583ebL

        /** How many of the 2^63 values are left over when they are split into six equal groups. */
        private const val REJECTED = (Long.MAX_VALUE % FACES + 1) % FACES

        private fun mix(value: Long): Long {
            var z = value
            z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
            z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
            return z xor (z ushr 31)
        }
    }
}
