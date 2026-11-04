package com.starsmuggler.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.starsmuggler.ui.sound.LocalSoundBoard
import com.starsmuggler.ui.sound.SoundEffect
import com.starsmuggler.ui.theme.InkFaded
import com.starsmuggler.ui.theme.RocketRed
import com.starsmuggler.ui.theme.RocketRedDark

/** How a stamp is inked: [Outline] is the usual rubber stamp, [Solid] the main action of a screen. */
enum class StampStyle { Outline, Solid }

/**
 * A button drawn as an inked rubber stamp: double frame, spaced capitals and a slight tilt,
 * like the seals of the Pavonis Sector Authority.
 */
@Composable
fun StampButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: StampStyle = StampStyle.Outline,
    enabled: Boolean = true,
    tilt: Float = -1.5f,
) {
    val interaction = remember { MutableInteractionSource() }
    val sounds = LocalSoundBoard.current
    val pressed by interaction.collectIsPressedAsState()
    val ink = when {
        !enabled -> InkFaded
        pressed -> RocketRedDark
        else -> RocketRed
    }
    val outer = RoundedCornerShape(6.dp)
    val inner = RoundedCornerShape(3.dp)
    val solid = style == StampStyle.Solid
    Box(
        modifier
            .rotate(if (pressed) 0f else tilt)
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button) {
                sounds?.play(SoundEffect.CLICK)
                onClick()
            }
            .border(2.dp, ink, outer)
            .padding(3.dp)
            .then(if (solid) Modifier.background(ink, inner) else Modifier.border(1.dp, ink, inner))
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = if (solid) MaterialTheme.colorScheme.onPrimary else ink,
        )
    }
}
