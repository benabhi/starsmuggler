package com.starsmuggler.engine.content

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** An event of the event book as data: how long it takes. Its text is a text asset keyed by [id]. */
@Serializable
data class GameEvent(val id: EventRef, val duration: EventDuration)

/** How long an event takes, from the "(toma ...)" of its heading in the event book. */
@Serializable
data class EventDuration(
    val kind: DurationKind,
    /** Fixed hours, or hours for each [per] unit. */
    val hours: Int? = null,
    /** Rolled hours, such as 1d6. */
    val dice: Dice? = null,
    /** What each [hours] is for: `round`, `roll`, `theft` or `mine`. */
    val per: String? = null,
    /** "Instantáneo": takes no time and happens at once. */
    val instant: Boolean = false,
    /** Takes [hours] only if the player is there. */
    val ifPresent: Boolean = false,
    /** The time of a block of events that do not state their own. */
    val defaulted: Boolean = false,
) {
    init {
        when (kind) {
            DurationKind.HOURS, DurationKind.PER -> require(hours != null && hours >= 0) { "Duration $kind needs hours" }
            DurationKind.DICE -> require(dice != null) { "Duration $kind needs dice" }
            else -> Unit
        }
        require((kind == DurationKind.PER) == (per != null)) { "Only a 'per' duration names its unit" }
    }
}

@Serializable
enum class DurationKind {
    @SerialName("hours") HOURS,
    @SerialName("dice") DICE,
    @SerialName("per") PER,
    @SerialName("rest_of_day") REST_OF_DAY,
    @SerialName("travel") TRAVEL,
    @SerialName("varies") VARIES,
    @SerialName("see_reference") SEE_REFERENCE,
    @SerialName("ends_game") ENDS_GAME,
}
