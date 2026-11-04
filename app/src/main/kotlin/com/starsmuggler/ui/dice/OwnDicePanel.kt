package com.starsmuggler.ui.dice

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.starsmuggler.R
import com.starsmuggler.engine.dice.RollRequest
import com.starsmuggler.engine.dice.Rng
import com.starsmuggler.ui.Formats
import com.starsmuggler.ui.components.RecordCard
import com.starsmuggler.ui.components.StampButton
import com.starsmuggler.ui.components.StampStyle
import com.starsmuggler.ui.currentLocale
import com.starsmuggler.ui.theme.Ink
import com.starsmuggler.ui.theme.InkFaded
import com.starsmuggler.ui.theme.Paper
import com.starsmuggler.ui.theme.RocketRed
import com.starsmuggler.ui.theme.TeletypeStyles

/**
 * Asks for the faces of the player's own dice (PRD P7, SMUG-30, after irondroid's OwnDicePanel):
 * what to roll and for which rule, then one row of 1 to 6 per die. Closing it cancels the roll,
 * and with it the action, which leaves the game as it was.
 */
@Composable
fun OwnDiceDialog(request: RollRequest, onRoll: (List<Int>) -> Unit, onCancel: () -> Unit) {
    Dialog(onDismissRequest = onCancel) {
        RecordCard(stringResource(R.string.own_dice_title)) {
            OwnDiceEntry(request, onRoll, onCancel)
        }
    }
}

/** A quick picker for the faces of the player's own dice: one row of 1 to 6 per die. */
@Composable
fun OwnDiceEntry(request: RollRequest, onRoll: (List<Int>) -> Unit, onCancel: () -> Unit) {
    val faces = remember(request) { mutableStateListOf<Int?>().apply { repeat(request.dice.count) { add(null) } } }
    val locale = currentLocale()
    val rule = request.table ?: request.rule.id
    Column(Modifier.padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            if (request.modifier == 0) {
                stringResource(R.string.own_dice_prompt, request.dice.toString(), rule)
            } else {
                stringResource(R.string.own_dice_prompt_modified, request.dice.toString(), rule, Formats.signed(request.modifier, locale))
            },
            style = MaterialTheme.typography.titleMedium,
            color = RocketRed,
        )
        for (die in faces.indices) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (face in 1..Rng.FACES) {
                    val picked = faces[die] == face
                    Box(
                        Modifier
                            .size(FACE_SIZE)
                            .border(2.dp, if (picked) RocketRed else InkFaded)
                            .background(if (picked) RocketRed else Color.Transparent)
                            .clickable { faces[die] = face },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(Formats.integer(face, locale), style = TeletypeStyles.medium, color = if (picked) Paper else Ink)
                    }
                }
            }
        }
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StampButton(
                stringResource(R.string.own_dice_use),
                style = StampStyle.Solid,
                enabled = faces.all { it != null },
                onClick = { onRoll(faces.map { it!! }) },
            )
            StampButton(stringResource(R.string.own_dice_cancel), onClick = onCancel)
        }
    }
}

/** Big enough to hit with a thumb, six to a row. */
private val FACE_SIZE = 40.dp
