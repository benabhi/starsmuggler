package com.starsmuggler.ui.dice

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.starsmuggler.R
import com.starsmuggler.dice.DiceSound
import com.starsmuggler.dice.DiceTable
import com.starsmuggler.dice.ThrownDie
import com.starsmuggler.engine.dice.DiceHand
import com.starsmuggler.engine.dice.DiceRoll
import com.starsmuggler.engine.dice.RollSource
import com.starsmuggler.ui.Formats
import com.starsmuggler.ui.currentLocale
import com.starsmuggler.ui.theme.Ink
import com.starsmuggler.ui.theme.Paper
import kotlinx.coroutines.delay

/**
 * The engine's dice shown on screen (SMUG-28): every roll the engine makes plays the irondroid roll
 * sound, throws the 3D dice so they land on the faces the engine chose, and then shows what was
 * rolled, with its modifier, while the dice stay on the table. Without a [table] (previews, or a
 * page that failed to load) the roll is only heard and written.
 */
class AnimatedDice(private val table: DiceTable?, private val sound: DiceSound?) : DiceHand {
    /** The roll the dice show now, until it is read; null between rolls. */
    var shown: ShownRoll? by mutableStateOf(null)
        private set

    private var count = 0

    override suspend fun show(roll: DiceRoll) {
        // The player's own dice are already on their table.
        if (roll.source == RollSource.PHYSICAL) return
        sound?.play()
        table?.roll(ThrownDie.of(roll.values))
        shown = ShownRoll(roll, ++count)
    }

    /** Loads the dice page ahead of the first roll. */
    fun preload() {
        table?.preload()
    }

    internal fun read(roll: ShownRoll) {
        if (shown == roll) shown = null
    }
}

/** One roll on screen; [id] tells apart two rolls with the same faces. */
data class ShownRoll(val roll: DiceRoll, val id: Int)

/** The activity's dice; a silent hand without 3D dice where there is none (previews). */
val LocalDice = staticCompositionLocalOf { AnimatedDice(null, null) }

/** What the dice show, written under them while they stay on the table. */
@Composable
fun BoxScope.RollCaption(dice: AnimatedDice) {
    val shown = dice.shown ?: return
    LaunchedEffect(shown) {
        delay(DiceTable.HOLD_MS)
        dice.read(shown)
    }
    Box(
        Modifier
            .align(Alignment.BottomCenter)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(start = 24.dp, end = 24.dp, bottom = CAPTION_BOTTOM)
            .fillMaxWidth()
            .background(Paper)
            .border(2.dp, Ink)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            rollCaption(shown.roll),
            style = MaterialTheme.typography.titleMedium,
            color = Ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun rollCaption(roll: DiceRoll): String {
    val locale = currentLocale()
    val rule = roll.request.table ?: roll.request.rule.id
    val dice = roll.request.dice.toString()
    val faces = roll.values.joinToString(FACE_SEPARATOR) { Formats.integer(it, locale) }
    val total = Formats.integer(roll.total, locale)
    val modifier = roll.request.modifier
    return when {
        modifier != 0 -> stringResource(R.string.dice_caption_modified, rule, dice, faces, Formats.signed(modifier, locale), total)
        roll.values.size == 1 -> stringResource(R.string.dice_caption_single, rule, dice, faces)
        else -> stringResource(R.string.dice_caption, rule, dice, faces, total)
    }
}

private const val FACE_SEPARATOR = " + "

/** Above the tab bar, so the caption does not hide it. */
private val CAPTION_BOTTOM = 96.dp
