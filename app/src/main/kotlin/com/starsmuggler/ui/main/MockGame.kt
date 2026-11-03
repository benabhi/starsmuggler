package com.starsmuggler.ui.main

import androidx.annotation.StringRes
import androidx.compose.runtime.saveable.listSaver
import com.starsmuggler.R
import com.starsmuggler.ui.components.HOURS_PER_DAY

/** Days in a game week; the loan interest is due when one ends (r203e). */
const val DAYS_PER_WEEK = 10

/**
 * Fake game state for the main screen mock-up (SMUG-5). It only moves the clock and the
 * loan so the layout can be judged on the phone; the rules engine replaces it in plan 3.
 */
data class MockGame(
    val week: Int = 1,
    val day: Int = 3,
    val hoursUsed: Int = 4,
    val money: Int = 2450,
    val weeklyInterest: Int = 300,
) {
    val hoursLeft: Int get() = HOURS_PER_DAY - hoursUsed

    /** Days left before the interest payment at the end of the week; 0 means today. */
    val daysToPayment: Int get() = DAYS_PER_WEEK - day

    fun spend(hours: Int): MockGame = copy(hoursUsed = (hoursUsed + hours).coerceAtMost(HOURS_PER_DAY))

    fun endDay(): MockGame = if (day < DAYS_PER_WEEK) {
        copy(day = day + 1, hoursUsed = 0)
    } else {
        copy(week = week + 1, day = 1, hoursUsed = 0, money = money - weeklyInterest)
    }
}

/** Keeps the mock game across rotation-free recreations, such as a theme or font change. */
val MockGameSaver = listSaver<MockGame, Int>(
    save = { listOf(it.week, it.day, it.hoursUsed, it.money, it.weeklyInterest) },
    restore = { MockGame(it[0], it[1], it[2], it[3], it[4]) },
)

/** One fake action on the bridge, with its cost and, if it cannot be taken, why. */
data class MockAction(
    @StringRes val title: Int,
    @StringRes val description: Int?,
    val hours: Int,
    val ruleRef: String,
    @StringRes val disabledReason: Int? = null,
)

/** A whole-day action costs every hour that is left (RRR, r203). */
const val WHOLE_DAY = -1

val mockActions = listOf(
    MockAction(R.string.mock_action_walk, R.string.mock_action_walk_desc, 2, "r203a"),
    MockAction(R.string.mock_action_contact, R.string.mock_action_contact_desc, 1, "r233"),
    MockAction(R.string.mock_action_trade, R.string.mock_action_trade_desc, 1, "r229"),
    MockAction(R.string.mock_action_rrr, R.string.mock_action_rrr_desc, WHOLE_DAY, "r203"),
    MockAction(R.string.mock_action_hopper, null, 1, "r214", disabledReason = R.string.mock_action_hopper_disabled),
)
