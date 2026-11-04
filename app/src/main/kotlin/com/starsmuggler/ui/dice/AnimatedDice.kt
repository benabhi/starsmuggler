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
import com.starsmuggler.engine.dice.RollRequest
import com.starsmuggler.engine.dice.RollSource
import com.starsmuggler.ui.Formats
import com.starsmuggler.ui.currentLocale
import com.starsmuggler.ui.theme.Ink
import com.starsmuggler.ui.theme.Paper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay

/**
 * The engine's dice shown on screen (SMUG-28): every roll the engine makes plays the irondroid roll
 * sound, throws the 3D dice so they land on the faces the engine chose, and then shows what was
 * rolled, with its modifier, while the dice stay on the table. The player's [mode] says which rolls
 * get the 3D dice (SMUG-29); the rest are only heard and written, as without a [table] (previews,
 * or a page that failed to load). With [ownDice] the player rolls on their own table and types the
 * faces in (SMUG-30).
 */
class AnimatedDice(
    private val table: DiceTable?,
    private val sound: DiceSound?,
    private val settings: DiceSettings? = null,
    /** Whether the app may make sounds (SMUG-31). */
    private val soundOn: () -> Boolean = { true },
) : DiceHand {
    /** Which rolls the 3D dice show (PRD P7). */
    var mode: DiceMode by mutableStateOf(settings?.mode ?: DiceMode.IMPORTANT)
        private set

    /** The player rolls their own dice and enters the faces (PRD P7). */
    var ownDice: Boolean by mutableStateOf(settings?.ownDice ?: false)
        private set

    /** A roll of the player's own dice waiting for its faces; null when none is. */
    var pending: PendingOwnRoll? by mutableStateOf(null)
        private set

    /** The roll the dice show now, until it is read; null between rolls. */
    var shown: ShownRoll? by mutableStateOf(null)
        private set

    private var count = 0

    /** Waits for the faces of the player's dice; cancelling the entry cancels the action. */
    override suspend fun physical(request: RollRequest): List<Int>? {
        if (!ownDice) return null
        val roll = PendingOwnRoll(request, CompletableDeferred())
        pending = roll
        try {
            return roll.answer.await()
        } finally {
            if (pending == roll) pending = null
        }
    }

    override suspend fun show(roll: DiceRoll) {
        // The player's own dice are already on their table: only what they entered is written.
        if (roll.source == RollSource.APP) {
            if (soundOn()) sound?.play()
            if (mode.throws(roll.request)) table?.roll(ThrownDie.of(roll.values))
        }
        shown = ShownRoll(roll, ++count)
    }

    fun changeOwnDice(ownDice: Boolean) {
        this.ownDice = ownDice
        settings?.ownDice = ownDice
    }

    fun changeMode(mode: DiceMode) {
        this.mode = mode
        settings?.mode = mode
    }

    /** Loads the dice page ahead of the first roll, unless the 3D dice are off. */
    fun preload() {
        if (mode != DiceMode.NONE) table?.preload()
    }

    internal fun read(roll: ShownRoll) {
        if (shown == roll) shown = null
    }
}

/** A roll of the player's own dice: the [answer] gets the faces, or is cancelled with the action. */
class PendingOwnRoll(val request: RollRequest, val answer: CompletableDeferred<List<Int>>)

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
