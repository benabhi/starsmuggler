package com.starsmuggler.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// Light "paper" theme only in the MVP; the night "cockpit" theme comes later (PRD P16).
private val PaperColors = lightColorScheme(
    primary = RocketRed,
    onPrimary = Paper,
    primaryContainer = RocketRedDark,
    onPrimaryContainer = Paper,
    secondary = ChromeTurquoise,
    onSecondary = Paper,
    tertiary = ChromeOchre,
    onTertiary = SpaceBlack,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = PaperShade,
    onSurfaceVariant = InkFaded,
    outline = Ink,
    outlineVariant = InkFaded,
    inverseSurface = SpaceBlack,
    inverseOnSurface = Paper,
)

@Composable
fun StarSmugglerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PaperColors,
        typography = StarSmugglerTypography,
        content = content,
    )
}
