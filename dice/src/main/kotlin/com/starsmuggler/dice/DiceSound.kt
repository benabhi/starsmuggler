package com.starsmuggler.dice

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

/**
 * The sound of a roll, the same clip as irondroid (benabhi, 03/11/2026): loaded once and played as
 * the dice are thrown. It plays through the media volume, so the phone's volume and silent mode
 * still rule it.
 */
class DiceSound(context: Context) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val clip = pool.load(context, R.raw.die_roll, 1)

    /** Plays the roll; a clip that has not finished loading yet is skipped, as the game never waits for it. */
    fun play() {
        pool.play(clip, 1f, 1f, 1, 0, 1f)
    }

    fun release() {
        pool.release()
    }
}
