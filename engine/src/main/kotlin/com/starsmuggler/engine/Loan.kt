package com.starsmuggler.engine

import kotlinx.serialization.Serializable

/**
 * The loan on the Antelope (r203e): 120,000 S of principal at 300 S of interest per week. Missing
 * an interest payment makes the whole principal due within 30 days; after that the loan company
 * tries to repossess the ship (e002).
 */
@Serializable
data class Loan(
    val principal: Int = PRINCIPAL,
    /** The day an interest payment was missed, or null while the loan is in good standing. */
    val defaultedOn: Int? = null,
    /** The 30 days went by without paying the principal: repossession events can happen. */
    val repossession: Boolean = false,
) {
    init {
        require(principal in 0..PRINCIPAL) { "Principal $principal outside 0..$PRINCIPAL" }
    }

    val isPaidOff: Boolean get() = principal == 0

    val isDefaulted: Boolean get() = defaultedOn != null && !isPaidOff

    /** The last day to pay the whole principal after a default. */
    val deadline: Int? get() = defaultedOn?.plus(GRACE_DAYS)

    /** Days until the next interest payment, counting today, on [clock]; null when no interest falls due. */
    fun interestDueIn(clock: Clock): Int? =
        if (isPaidOff || isDefaulted) null else Clock.DAYS_PER_WEEK - clock.dayOfWeek + 1

    companion object {
        const val PRINCIPAL = 120_000
        const val WEEKLY_INTEREST = 300
        const val GRACE_DAYS = 30
    }
}
