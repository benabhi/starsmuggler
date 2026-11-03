package com.starsmuggler.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.starsmuggler.ui.theme.ChromeOchre
import com.starsmuggler.ui.theme.Ink
import com.starsmuggler.ui.theme.InkFaded
import com.starsmuggler.ui.theme.PaperShade
import com.starsmuggler.ui.theme.RocketRed
import com.starsmuggler.ui.theme.TeletypeStyles

/**
 * One action the player can take now (PRD section 7, bridge): its name, a short explanation,
 * its cost in hours and, when it cannot be taken, the reason why.
 */
@Composable
fun ActionCard(
    title: String,
    cost: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    disabledReason: String? = null,
    ruleRef: String? = null,
) {
    val enabled = disabledReason == null
    val shape = RoundedCornerShape(4.dp)
    Row(
        modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .background(PaperShade)
            .border(1.5.dp, if (enabled) Ink else InkFaded, shape)
            .padding(end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The coloured edge of a brochure tab: red when the action can be taken.
        Box(
            Modifier
                .width(8.dp)
                .fillMaxHeight()
                .background(if (enabled) RocketRed else InkFaded),
        )
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .alpha(if (enabled) 1f else 0.6f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(title.uppercase(), style = MaterialTheme.typography.titleSmall, color = Ink)
            if (description != null) {
                Text(description, style = MaterialTheme.typography.bodySmall, color = Ink)
            }
            if (disabledReason != null) {
                Text(
                    disabledReason,
                    style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                    color = RocketRed,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(cost, style = TeletypeStyles.large, color = if (enabled) Ink else InkFaded)
            if (ruleRef != null) {
                Text(ruleRef, style = TeletypeStyles.small, color = ChromeOchre)
            }
        }
    }
}
