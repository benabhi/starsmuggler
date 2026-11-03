package com.starsmuggler.engine

import kotlinx.serialization.Serializable

/**
 * The game clock (r203): days of ten activity hours in weeks of ten days. Time is counted in
 * tenths of an hour, since a starship or a Hopper spends 1/10 hour per land step (r203a).
 */
@Serializable
data class Clock(
    /** Day of the game, 1 for the first one. */
    val day: Int = 1,
    /** Tenths of an hour used today; past [DAY] they are overtime. */
    val used: Int = 0,
) {
    init {
        require(day >= 1) { "Day $day before the first one" }
        require(used >= 0) { "Negative time used: $used" }
    }

    /** Week of the game, 1 for days 1 to 10. */
    val week: Int get() = (day - 1) / DAYS_PER_WEEK + 1

    /** Day within the week, 1 to 10. */
    val dayOfWeek: Int get() = (day - 1) % DAYS_PER_WEEK + 1

    /** Tenths of an hour left today, 0 in overtime. */
    val left: Int get() = (DAY - used).coerceAtLeast(0)

    /** Tenths of an hour of overtime an event took today (r203). */
    val overtime: Int get() = (used - DAY).coerceAtLeast(0)

    /** No time has been used today, as a full day of rest needs (r203c). */
    val isFresh: Boolean get() = used == 0

    /** Whether an action of [tenths] can still start today. */
    fun fits(tenths: Int): Boolean = tenths <= left

    fun spend(tenths: Int): Clock {
        require(tenths >= 0) { "Negative time: $tenths" }
        return copy(used = used + tenths)
    }

    /** The first moment of the next day. */
    fun nextDay(): Clock = Clock(day + 1)

    /** Whether today closes a week, when the loan payment falls due (r203e). */
    val endsWeek: Boolean get() = dayOfWeek == DAYS_PER_WEEK

    companion object {
        /** Tenths in one hour. */
        const val HOUR = 10

        /** Activity time in one day: ten hours (r203). */
        const val DAY = 10 * HOUR
        const val DAYS_PER_WEEK = 10
    }
}
