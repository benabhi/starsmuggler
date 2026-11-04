package com.starsmuggler.engine

import com.starsmuggler.engine.content.EventRef
import com.starsmuggler.engine.content.Outcome
import com.starsmuggler.engine.dice.DiceRoll
import com.starsmuggler.engine.dice.Rng
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A game at one moment. It is never changed: each action makes a new state. It holds ids and
 * values only, never text, and is serializable so it can be saved as it is.
 */
@Serializable
data class GameState(
    /** The [Engine.RULES_VERSION] the game was played with. */
    val rulesVersion: Int,
    /** The seed the game started with. */
    val seed: Long,
    /** The generator after the last die the engine rolled. */
    val rng: Rng,
    /** How many actions have been performed. */
    val step: Int,
    val clock: Clock = Clock(),
    val duke: Duke,
    val ship: Ship = Ship(),
    /** Cash in Duke's pocket, in Sees (r232a). */
    val cash: Int,
    val loan: Loan = Loan(),
    val location: Location = Location.START,
    val standing: Standing = Standing(),
    /** Gear used today, checked for breakdowns when the day ends (r203d). */
    val usedToday: List<GearRef> = emptyList(),
    /** The last preventive maintenance (r218b). */
    val maintenance: Maintenance? = null,
    /** How the game ended, or null while it goes on. */
    val ending: Ending? = null,
    /** The game's history, oldest first (PRD section 7, log). */
    val log: List<LogEntry>,
)

/** How a game ends (r203f). */
@Serializable
enum class Ending {
    /** The player stops playing: alive and free, with whatever money is left. */
    RETIRED,

    /** Duke died: the only true defeat. */
    DIED,
}

/** A game is won by ending it alive with the whole loan paid off (r203f); the money kept says by how much. */
val GameState.won: Boolean get() = ending == Ending.RETIRED && loan.isPaidOff

/** One line of the game's history, written by the action of number [step]. The interface turns it into text. */
@Serializable
sealed interface LogEntry {
    val step: Int

    @Serializable
    @SerialName("started")
    data class GameStarted(override val step: Int, val seed: Long, val rulesVersion: Int) : LogEntry

    /** Astucia set at the start: rolled, or the [previous] game's one changed by [adjustment] (r202). */
    @Serializable
    @SerialName("astucia_set")
    data class AstuciaSet(override val step: Int, val astucia: Int, val previous: Int? = null, val adjustment: Int = 0) : LogEntry

    /** The cash Duke starts with (e001). */
    @Serializable
    @SerialName("starting_cash")
    data class StartingCash(override val step: Int, val sees: Int) : LogEntry

    /** An Astucia roll: [total] against [astucia], a success when it is not higher (r202). */
    @Serializable
    @SerialName("astucia_roll")
    data class AstuciaRolled(override val step: Int, val astucia: Int, val total: Int, val success: Boolean) : LogEntry

    /** A roll of the dice, made by the app or by the player. */
    @Serializable
    @SerialName("rolled")
    data class Rolled(override val step: Int, val roll: DiceRoll) : LogEntry

    /** [tenths] of an hour went by; past the day's ten hours they were [overtime] (r203). */
    @Serializable
    @SerialName("time")
    data class TimePassed(override val step: Int, val tenths: Int, val overtime: Int = 0) : LogEntry

    /** Day [day] of the game closed, after [overtime] tenths of an hour past its ten (r203). */
    @Serializable
    @SerialName("day_ended")
    data class DayEnded(override val step: Int, val day: Int, val overtime: Int = 0) : LogEntry

    /** Week [week] closed with its last day: the loan payment falls due (r203e). */
    @Serializable
    @SerialName("week_ended")
    data class WeekEnded(override val step: Int, val week: Int) : LogEntry

    /** The weekly interest of the loan was paid from cash (r203e). */
    @Serializable
    @SerialName("interest_paid")
    data class InterestPaid(override val step: Int, val week: Int, val sees: Int) : LogEntry

    /** The interest of [week] could not be paid: the whole [principal] is due by day [deadline] (r203e). */
    @Serializable
    @SerialName("loan_defaulted")
    data class LoanDefaulted(override val step: Int, val week: Int, val principal: Int, val deadline: Int) : LogEntry

    /** The 30 days after a default went by: the loan company tries to repossess the ship from [day] on (r203e, e002). */
    @Serializable
    @SerialName("repossession")
    data class RepossessionStarted(override val step: Int, val day: Int) : LogEntry

    /** [sees] of principal paid; [principal] is what is still owed (r203e). */
    @Serializable
    @SerialName("principal_paid")
    data class PrincipalPaid(override val step: Int, val sees: Int, val principal: Int) : LogEntry

    /** The whole loan is paid off: the victory of r203f, by as much as the money kept. */
    @Serializable
    @SerialName("loan_paid_off")
    data class LoanPaidOff(override val step: Int) : LogEntry

    /** Cash changed by [sees] for an event resolved by hand (PRD P2); [cash] is the new amount. */
    @Serializable
    @SerialName("cash_adjusted")
    data class CashAdjusted(override val step: Int, val sees: Int, val cash: Int) : LogEntry

    /** [gear] was used today, so it is checked for breakdowns tonight (r203d). */
    @Serializable
    @SerialName("gear_used")
    data class GearUsed(override val step: Int, val gear: GearRef) : LogEntry

    /** End-of-day check of [gear] of tech [tech]: 2d6 plus maintenance gave [total]; at most [tech] it [broke] (r218a). */
    @Serializable
    @SerialName("breakdown_check")
    data class BreakdownChecked(override val step: Int, val gear: GearRef, val tech: Int, val total: Int, val broke: Boolean) : LogEntry

    /** Preventive maintenance by [by] (r218b). */
    @Serializable
    @SerialName("maintenance")
    data class MaintenanceDone(override val step: Int, val by: Maintainer) : LogEntry

    /** A change written on the record sheet by hand (PRD P2). */
    @Serializable
    @SerialName("sheet_edited")
    data class SheetEdited(override val step: Int, val edit: SheetEdit) : LogEntry

    /** The game ended: [won] or not, on [day], with [cash] in hand and [debt] of principal still owed. */
    @Serializable
    @SerialName("game_ended")
    data class GameEnded(
        override val step: Int,
        val ending: Ending,
        val won: Boolean,
        val day: Int,
        val cash: Int,
        val debt: Int,
    ) : LogEntry

    /** The starship moved one hour with its sublight drive [from] one place [to] another (r212g). */
    @Serializable
    @SerialName("ship_moved")
    data class ShipMoved(override val step: Int, val from: ShipPosition, val to: ShipPosition) : LogEntry

    /**
     * A hyperspace jump [from] one system [to] another over [hops] routes, using [hypercharges]: with
     * [risk] above 0 the 1d6 [roll] had to beat it (r212c). It [succeeded], or the ship dissipated (r212d).
     */
    @Serializable
    @SerialName("jumped")
    data class Jumped(
        override val step: Int,
        val from: String,
        val to: String,
        val hops: Int,
        val hypercharges: Int,
        val risk: Int,
        val roll: Int? = null,
        val succeeded: Boolean,
    ) : LogEntry

    /** The ship dissipated in a jump to [system]; Duke was [inStasis] or died with it (r212d-e). */
    @Serializable
    @SerialName("dissipated")
    data class Dissipated(override val step: Int, val system: String, val inStasis: Boolean) : LogEntry

    /** Duke's stasis unit was opened and he wakes up at [place] with [cash] left, his ship and gear gone (r240). */
    @Serializable
    @SerialName("stasis_recovered")
    data class StasisRecovered(override val step: Int, val place: Location, val cash: Int) : LogEntry

    /** Arriving from hyperspace in [system], its entry table gave [event] for the player to resolve (r226, r207). */
    @Serializable
    @SerialName("system_entry")
    data class SystemEntry(override val step: Int, val system: String, val event: EventRef) : LogEntry

    /**
     * Entering [place] by [mode] (a row of r238): a 1d6 [roll] not higher than the detection
     * [factor] means Duke was [detected], and an entry encounter follows on r233 (r225).
     */
    @Serializable
    @SerialName("detection")
    data class DetectionChecked(
        override val step: Int,
        val place: Location,
        val mode: String,
        val factor: Int,
        val roll: Int,
        val detected: Boolean,
    ) : LogEntry

    /** The result of a modified roll [total] on [table], in its row [key]; null when no entry covers the roll. */
    @Serializable
    @SerialName("table")
    data class TableResult(
        override val step: Int,
        val table: String,
        val key: String? = null,
        val total: Int,
        val outcome: Outcome?,
    ) : LogEntry
}
