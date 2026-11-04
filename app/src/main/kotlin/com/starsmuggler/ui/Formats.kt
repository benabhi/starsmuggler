package com.starsmuggler.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.starsmuggler.R
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Locale

/**
 * Numbers as the player's language writes them (PRD section 10): every figure on screen goes
 * through here instead of `toString()`, so another language gets its own grouping for free.
 */
object Formats {
    /** A whole number with the locale's grouping: 12.000 in Spanish, 12,000 in English. */
    fun integer(value: Int, locale: Locale): String = NumberFormat.getIntegerInstance(locale).format(value)

    /** A whole number with its sign, such as +1 or -1 for an adjustment; zero has none. */
    fun signed(value: Int, locale: Locale): String =
        if (value > 0) (NumberFormat.getIntegerInstance(locale) as DecimalFormat).apply { positivePrefix = "+" }.format(value) else integer(value, locale)

    /** A number with up to one decimal, such as 1,5 in Spanish (price multipliers, r241). */
    fun decimal(value: Double, locale: Locale): String =
        NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1 }.format(value)
}

/** The locale the screen is drawn in. */
@Composable
fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

/** An amount of money in Sees, such as "12.000 S" (r232a). */
@Composable
fun formatSees(amount: Int): String = stringResource(R.string.money_sees, Formats.integer(amount, currentLocale()))

/** A cost in activity hours, such as "2 h" (r203). */
@Composable
fun formatHours(hours: Int): String = pluralStringResource(R.plurals.cost_hours, hours, Formats.integer(hours, currentLocale()))
