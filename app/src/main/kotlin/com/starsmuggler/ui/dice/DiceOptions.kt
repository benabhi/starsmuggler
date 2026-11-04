package com.starsmuggler.ui.dice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.starsmuggler.R
import com.starsmuggler.ui.theme.Ink
import com.starsmuggler.ui.theme.InkFaded
import com.starsmuggler.ui.theme.RocketRed

/** The dice options (PRD P7, SMUG-29): which rolls the 3D dice show. */
@Composable
fun DiceOptions(dice: AnimatedDice, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.dice_options_title), style = MaterialTheme.typography.titleMedium, color = RocketRed)
        DiceMode.entries.forEach { mode ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .selectable(selected = dice.mode == mode, role = Role.RadioButton) { dice.changeMode(mode) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = dice.mode == mode,
                    onClick = null,
                    colors = RadioButtonDefaults.colors(selectedColor = RocketRed, unselectedColor = Ink),
                )
                Column(Modifier.padding(start = 12.dp)) {
                    Text(stringResource(mode.label), style = MaterialTheme.typography.bodyLarge, color = Ink)
                    Text(stringResource(mode.hint), style = MaterialTheme.typography.bodySmall, color = InkFaded)
                }
            }
        }
    }
}

private val DiceMode.label: Int
    get() = when (this) {
        DiceMode.ALL -> R.string.dice_mode_all
        DiceMode.IMPORTANT -> R.string.dice_mode_important
        DiceMode.NONE -> R.string.dice_mode_none
    }

private val DiceMode.hint: Int
    get() = when (this) {
        DiceMode.ALL -> R.string.dice_mode_all_hint
        DiceMode.IMPORTANT -> R.string.dice_mode_important_hint
        DiceMode.NONE -> R.string.dice_mode_none_hint
    }
