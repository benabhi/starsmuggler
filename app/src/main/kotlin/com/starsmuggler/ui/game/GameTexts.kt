package com.starsmuggler.ui.game

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.starsmuggler.R
import com.starsmuggler.content.LoadedContent
import com.starsmuggler.engine.Clock
import com.starsmuggler.engine.GameState
import com.starsmuggler.engine.GearRef
import com.starsmuggler.engine.LogEntry
import com.starsmuggler.engine.Maintainer
import com.starsmuggler.engine.Rejection
import com.starsmuggler.engine.content.Outcome
import com.starsmuggler.engine.dice.DiceRoll
import com.starsmuggler.engine.dice.RollSource
import com.starsmuggler.ui.Formats
import com.starsmuggler.ui.currentLocale
import com.starsmuggler.ui.formatSees

/*
 * Texts of the game state shared by the bridge, the log and the engine bench: the engine returns
 * ids and values (PRD section 10) and these turn them into the player's language.
 */

const val STARSHIP_GUNS = "starship_guns" // not-ui

/** Where the save slots live, under the app's files. */
const val SAVES_DIR = "saves" // not-ui

private const val DICE_SEPARATOR = " + "

@Composable
fun gearName(ref: GearRef, game: GameState, content: LoadedContent): String = when (ref) {
    GearRef.Starship -> stringResource(R.string.engine_ship)
    GearRef.StarshipGuns -> content.names.equipment(STARSHIP_GUNS)
    GearRef.Boat -> stringResource(R.string.engine_boat)
    is GearRef.DukeItem -> game.duke.items.getOrNull(ref.index)?.let { content.names.equipment(it.id) } ?: stringResource(R.string.engine_gear_missing)
}

/** Tenths of an hour as hours with one decimal, such as "6,5 h". */
@Composable
fun hoursText(tenths: Int): String = stringResource(R.string.engine_hours, Formats.decimal(tenths / Clock.HOUR.toDouble(), currentLocale()))

@Composable
fun logText(entry: LogEntry, game: GameState, content: LoadedContent): String {
    val locale = currentLocale()
    val step = Formats.integer(entry.step, locale)
    return when (entry) {
        is LogEntry.GameStarted -> stringResource(R.string.engine_log_started, step, entry.seed.toString())
        is LogEntry.Rolled -> rolledText(step, entry.roll)
        is LogEntry.AstuciaSet -> {
            val previous = entry.previous
            if (previous == null) {
                stringResource(R.string.engine_log_astucia_rolled, step, Formats.integer(entry.astucia, locale))
            } else {
                stringResource(
                    R.string.engine_log_astucia_adjusted,
                    step,
                    Formats.integer(entry.astucia, locale),
                    Formats.integer(previous, locale),
                    Formats.signed(entry.adjustment, locale),
                )
            }
        }
        is LogEntry.StartingCash -> stringResource(R.string.engine_log_starting_cash, step, formatSees(entry.sees))
        is LogEntry.InterestPaid -> stringResource(R.string.engine_log_interest_paid, step, formatSees(entry.sees), Formats.integer(entry.week, locale))
        is LogEntry.LoanDefaulted -> stringResource(
            R.string.engine_log_loan_defaulted,
            step,
            Formats.integer(entry.week, locale),
            formatSees(entry.principal),
            Formats.integer(entry.deadline, locale),
        )
        is LogEntry.RepossessionStarted -> stringResource(R.string.engine_log_repossession, step, Formats.integer(entry.day, locale))
        is LogEntry.PrincipalPaid -> stringResource(R.string.engine_log_principal_paid, step, formatSees(entry.sees), formatSees(entry.principal))
        is LogEntry.LoanPaidOff -> stringResource(R.string.engine_log_loan_paid_off, step)
        is LogEntry.GearUsed -> stringResource(R.string.engine_log_gear_used, step, gearName(entry.gear, game, content))
        is LogEntry.BreakdownChecked -> stringResource(
            if (entry.broke) R.string.engine_log_breakdown else R.string.engine_log_no_breakdown,
            step,
            gearName(entry.gear, game, content),
            Formats.integer(entry.tech, locale),
            Formats.integer(entry.total, locale),
        )
        is LogEntry.MaintenanceDone -> stringResource(
            R.string.engine_log_maintenance,
            step,
            stringResource(if (entry.by == Maintainer.ENGINEER) R.string.engine_maintainer_engineer else R.string.engine_maintainer_robot),
        )
        is LogEntry.CashAdjusted -> stringResource(R.string.engine_log_cash_adjusted, step, formatSignedSees(entry.sees), formatSees(entry.cash))
        is LogEntry.AstuciaRolled -> stringResource(
            if (entry.success) R.string.engine_log_astucia_success else R.string.engine_log_astucia_failure,
            step,
            Formats.integer(entry.total, locale),
            Formats.integer(entry.astucia, locale),
        )
        is LogEntry.TimePassed -> if (entry.overtime == 0) {
            stringResource(R.string.engine_log_time, step, hoursText(entry.tenths))
        } else {
            stringResource(R.string.engine_log_time_overtime, step, hoursText(entry.tenths), hoursText(entry.overtime))
        }
        is LogEntry.DayEnded -> if (entry.overtime == 0) {
            stringResource(R.string.engine_log_day_ended, step, Formats.integer(entry.day, locale))
        } else {
            stringResource(R.string.engine_log_day_ended_overtime, step, Formats.integer(entry.day, locale), hoursText(entry.overtime))
        }
        is LogEntry.WeekEnded -> stringResource(R.string.engine_log_week_ended, step, Formats.integer(entry.week, locale))
        is LogEntry.TableResult -> stringResource(
            R.string.engine_log_table,
            step,
            entry.table,
            rowLabel(entry.table, entry.key, content),
            Formats.integer(entry.total, locale),
            entry.outcome?.let { outcomeText(it, content) } ?: stringResource(R.string.engine_no_outcome),
        )
    }
}

@Composable
fun rolledText(step: String, roll: DiceRoll): String {
    val locale = currentLocale()
    val faces = roll.values.joinToString(DICE_SEPARATOR) { Formats.integer(it, locale) }
    val source = stringResource(if (roll.source == RollSource.PHYSICAL) R.string.engine_source_own else R.string.engine_source_app)
    val modifier = roll.request.modifier
    return if (modifier == 0) {
        stringResource(R.string.engine_log_rolled, step, roll.request.dice.toString(), faces, Formats.integer(roll.total, locale), source)
    } else {
        stringResource(
            R.string.engine_log_rolled_modified,
            step,
            roll.request.dice.toString(),
            faces,
            Formats.integer(modifier, locale),
            Formats.integer(roll.total, locale),
            source,
        )
    }
}

fun rejectionText(reason: Rejection): Int = when (reason) {
    Rejection.UNKNOWN_TABLE -> R.string.engine_rejected_table
    Rejection.TABLE_WITHOUT_DICE -> R.string.engine_rejected_no_dice
    Rejection.UNKNOWN_ROW -> R.string.engine_rejected_row
    Rejection.NOT_ENOUGH_HOURS -> R.string.engine_rejected_hours
    Rejection.NO_TIME -> R.string.engine_rejected_no_time
    Rejection.NOT_ENOUGH_CASH -> R.string.engine_rejected_cash
    Rejection.INVALID_AMOUNT -> R.string.engine_rejected_amount
    Rejection.LOAN_PAID_OFF -> R.string.engine_rejected_paid_off
    Rejection.GEAR_MISSING -> R.string.engine_rejected_gear_missing
    Rejection.GEAR_BROKEN -> R.string.engine_rejected_gear_broken
    Rejection.ALREADY_MAINTAINED -> R.string.engine_rejected_maintained
}

/** A change of money with its sign, such as "+1.000 S". */
@Composable
fun formatSignedSees(sees: Int): String = stringResource(R.string.money_sees, Formats.signed(sees, currentLocale()))

@Composable
fun rowLabel(tableId: String, key: String?, content: LoadedContent): String = when {
    key == null -> stringResource(R.string.content_single_row)
    tableId == "r241" -> stringResource(R.string.content_row_wealth, key)
    tableId == "r235" -> stringResource(R.string.content_row_tech, key)
    tableId == "r238" -> content.names.travelMode(key)
    key in content.names.areaTypes -> content.names.areaType(key)
    key in content.names.systems -> content.names.system(key)
    else -> key
}

@Composable
fun outcomeText(outcome: Outcome, content: LoadedContent): String {
    val locale = currentLocale()
    return when (outcome) {
        is Outcome.Event -> outcome.ref.id
        is Outcome.Rule -> outcome.ref.id
        Outcome.SystemTable -> stringResource(R.string.content_result_system)
        Outcome.NoMoreRolls -> stringResource(R.string.content_result_stop)
        is Outcome.Key -> content.names.results[outcome.key] ?: content.names.goods(outcome.key)
        is Outcome.Multiplier -> stringResource(R.string.content_result_multiplier, Formats.decimal(outcome.value, locale))
        is Outcome.Value -> if (outcome.marked) {
            stringResource(R.string.content_result_marked, Formats.integer(outcome.value, locale))
        } else {
            Formats.integer(outcome.value, locale)
        }
    }
}
