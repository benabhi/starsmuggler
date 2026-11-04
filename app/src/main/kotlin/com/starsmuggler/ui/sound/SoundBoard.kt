package com.starsmuggler.ui.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.content.edit
import com.starsmuggler.R

/** The app's sound effects (PRD P11, SMUG-31), made by `tools/sound/gen_sounds.py`. */
enum class SoundEffect(val clip: Int) {
    /** A teletype key, on every button and action. */
    CLICK(R.raw.ui_click),

    /** The loan payment is two days away or less, at the start of a day (r203e). */
    LOAN_WARNING(R.raw.loan_warning),

    /** The cash went up, by any action (benabhi, 04/11/2026). */
    MONEY(R.raw.money_gained),

    /** A hyperspace jump (r212d); plays once travel arrives (plan 5). */
    JUMP(R.raw.hyperspace_jump),

    /** A shot in combat (r230d); plays once combat arrives (plan 8). */
    SHOT(R.raw.laser_shot),
}

/**
 * Plays the [SoundEffect]s through the media volume, so the phone's volume and silent mode still
 * rule them. The player turns every sound off with [enabled], kept on the phone; the dice obey it too.
 */
class SoundBoard(context: Context) {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val pool = SoundPool.Builder()
        .setMaxStreams(MAX_STREAMS)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val clips = SoundEffect.entries.associateWith { pool.load(context, it.clip, 1) }

    /** Whether the app makes any sound. */
    var enabled: Boolean by mutableStateOf(prefs.getBoolean(ENABLED, true))
        private set

    fun changeEnabled(enabled: Boolean) {
        this.enabled = enabled
        prefs.edit { putBoolean(ENABLED, enabled) }
    }

    /** Plays [effect]; a clip that has not finished loading yet is skipped, as the game never waits for it. */
    fun play(effect: SoundEffect) {
        if (enabled) pool.play(clips.getValue(effect), 1f, 1f, 1, 0, 1f)
    }

    fun release() {
        pool.release()
    }

    private companion object {
        const val FILE = "sound" // not-ui
        const val ENABLED = "enabled" // not-ui
        const val MAX_STREAMS = 4
    }
}

/** The activity's sounds; none where there is no activity (previews). */
val LocalSoundBoard = staticCompositionLocalOf<SoundBoard?> { null }
