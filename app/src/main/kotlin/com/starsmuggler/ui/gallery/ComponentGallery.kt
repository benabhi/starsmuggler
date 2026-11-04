package com.starsmuggler.ui.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.starsmuggler.R
import com.starsmuggler.ui.components.ActionCard
import com.starsmuggler.ui.formatHours
import com.starsmuggler.ui.components.HOURS_PER_DAY
import com.starsmuggler.ui.components.HourDial
import com.starsmuggler.ui.components.RecordCard
import com.starsmuggler.ui.components.RecordLine
import com.starsmuggler.ui.components.StampButton
import com.starsmuggler.ui.components.StampStyle
import com.starsmuggler.ui.sound.LocalSoundBoard
import com.starsmuggler.ui.sound.SoundEffect
import com.starsmuggler.ui.theme.ChromeOchre
import com.starsmuggler.ui.theme.ChromeTurquoise
import com.starsmuggler.ui.theme.Ink
import com.starsmuggler.ui.theme.InkFaded
import com.starsmuggler.ui.theme.Paper
import com.starsmuggler.ui.theme.PaperBackground
import com.starsmuggler.ui.theme.PaperShade
import com.starsmuggler.ui.theme.PlanetBlue
import com.starsmuggler.ui.theme.PlanetGreen
import com.starsmuggler.ui.theme.RocketRed
import com.starsmuggler.ui.theme.SpaceBlack
import com.starsmuggler.ui.theme.TeletypeStyles

/** Every base component of the retro theme on one screen, to review them on the phone (SMUG-4). */
@Composable
fun ComponentGallery() {
    var hoursUsed by remember { mutableIntStateOf(6) }
    PaperBackground(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                // Insets outside the scroll, so the text never slides under the system bars.
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.app_name).lowercase(), style = MaterialTheme.typography.displayLarge, color = RocketRed)
            Text(stringResource(R.string.gallery_subtitle), style = MaterialTheme.typography.labelMedium, color = InkFaded)

            Section(R.string.gallery_section_type)
            Text(stringResource(R.string.gallery_sample_headline), style = MaterialTheme.typography.headlineLarge, color = Ink)
            Text(stringResource(R.string.gallery_sample_tagline).uppercase(), style = MaterialTheme.typography.titleSmall, color = Ink)
            Text(stringResource(R.string.gallery_sample_body), style = MaterialTheme.typography.bodyLarge, color = Ink)
            Text(stringResource(R.string.gallery_sample_teletype), style = TeletypeStyles.medium, color = Ink)

            Section(R.string.gallery_section_stamps)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                StampButton(stringResource(R.string.gallery_stamp_end_day), onClick = { hoursUsed = 0 }, style = StampStyle.Solid)
                StampButton(stringResource(R.string.gallery_stamp_jump), onClick = {}, tilt = 1.5f)
                StampButton(stringResource(R.string.gallery_stamp_disabled), onClick = {}, enabled = false)
            }

            Section(R.string.gallery_section_actions)
            ActionCard(
                title = stringResource(R.string.gallery_action_contact),
                description = stringResource(R.string.gallery_action_contact_desc),
                cost = formatHours(1),
                ruleRef = "r233",
                onClick = { hoursUsed = (hoursUsed + 1).coerceAtMost(HOURS_PER_DAY) },
            )
            ActionCard(
                title = stringResource(R.string.gallery_action_rrr),
                description = stringResource(R.string.gallery_action_rrr_desc),
                cost = stringResource(R.string.cost_whole_day),
                ruleRef = "r203",
                onClick = { hoursUsed = HOURS_PER_DAY },
            )
            ActionCard(
                title = stringResource(R.string.gallery_action_hopper),
                cost = formatHours(2),
                disabledReason = stringResource(R.string.gallery_action_hopper_disabled),
                ruleRef = "r214",
                onClick = {},
            )

            Section(R.string.gallery_section_dial)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                val left = HOURS_PER_DAY - hoursUsed
                val leftText = pluralStringResource(R.plurals.dial_hours_left, left, left)
                HourDial(hoursUsed = hoursUsed.toFloat(), description = leftText)
                Text(leftText, style = MaterialTheme.typography.titleMedium, color = Ink)
            }

            Section(R.string.gallery_section_card)
            RecordCard(stringResource(R.string.gallery_card_title)) {
                RecordLine(stringResource(R.string.stat_marksmanship), "4")
                RecordLine(stringResource(R.string.stat_hand_to_hand), "3")
                RecordLine(stringResource(R.string.stat_endurance), "5")
                RecordLine(stringResource(R.string.stat_cunning), "2")
            }

            // Every sound effect, also the ones whose actions come in later plans (SMUG-31).
            LocalSoundBoard.current?.let { sounds ->
                Section(R.string.gallery_section_sounds)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    StampButton(stringResource(R.string.gallery_sound_loan), onClick = { sounds.play(SoundEffect.LOAN_WARNING) })
                    StampButton(stringResource(R.string.gallery_sound_jump), onClick = { sounds.play(SoundEffect.JUMP) })
                    StampButton(stringResource(R.string.gallery_sound_shot), onClick = { sounds.play(SoundEffect.SHOT) })
                }
            }

            Section(R.string.gallery_section_palette)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Paper, PaperShade, Ink, RocketRed, SpaceBlack, ChromeTurquoise, ChromeOchre, PlanetGreen, PlanetBlue)
                    .forEach { Swatch(it) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Section(title: Int) {
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        HorizontalDivider(thickness = 2.dp, color = Ink)
        Text(
            stringResource(title).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = RocketRed,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun Swatch(color: Color) {
    Box(Modifier.size(36.dp).background(color, CircleShape).border(1.dp, Ink, CircleShape))
}
