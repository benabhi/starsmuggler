package com.starsmuggler.ui.dice

import android.content.Context
import androidx.core.content.edit
import com.starsmuggler.engine.dice.RollRequest
import com.starsmuggler.engine.dice.important

/** Which rolls the 3D dice show (PRD P7, SMUG-29); the rest are only heard and written. */
enum class DiceMode {
    /** Every roll. */
    ALL,

    /** Combat, jumps, contacts and events: the default. */
    IMPORTANT,

    /** None: rolls are written only. */
    NONE;

    fun throws(request: RollRequest): Boolean = when (this) {
        ALL -> true
        IMPORTANT -> request.important
        NONE -> false
    }
}

/** The player's dice options, kept on the phone between sessions. */
class DiceSettings(context: Context) {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var mode: DiceMode
        get() = prefs.getString(MODE, null)?.let { saved -> DiceMode.entries.find { it.name == saved } } ?: DiceMode.IMPORTANT
        set(value) = prefs.edit { putString(MODE, value.name) }

    private companion object {
        const val FILE = "dice" // not-ui
        const val MODE = "mode" // not-ui
    }
}
