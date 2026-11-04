package com.starsmuggler.dice

/**
 * One d6 of a throw: the [value] it must land on and whether it is [ink] (dark with paper numbers)
 * or paper (light with ink numbers). Star Smuggler only rolls d6.
 */
data class ThrownDie(val value: Int, val ink: Boolean) {
    init {
        require(value in 1..SIDES) { "A d$SIDES cannot show $value" }
    }

    companion object {
        const val SIDES = 6

        /** The dice of a roll, in order: the first in ink and the rest in paper, so 2d6 are told apart. */
        fun of(values: List<Int>): List<ThrownDie> = values.mapIndexed { i, value -> ThrownDie(value, ink = i == 0) }
    }
}
