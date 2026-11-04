package com.starsmuggler.ui.sound

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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

/** The sound options (PRD P11, SMUG-31): every sound of the app on or off. */
@Composable
fun SoundOptions(sounds: SoundBoard, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.sound_options_title), style = MaterialTheme.typography.titleMedium, color = RocketRed)
        Row(
            Modifier
                .fillMaxWidth()
                .toggleable(value = sounds.enabled, role = Role.Switch) { sounds.changeEnabled(it) }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(stringResource(R.string.sound_effects), style = MaterialTheme.typography.bodyLarge, color = Ink)
                Text(stringResource(R.string.sound_effects_hint), style = MaterialTheme.typography.bodySmall, color = InkFaded)
            }
            Switch(
                checked = sounds.enabled,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(checkedTrackColor = RocketRed, uncheckedBorderColor = Ink, uncheckedThumbColor = Ink),
            )
        }
    }
}
