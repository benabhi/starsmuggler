package com.starsmuggler.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.starsmuggler.R

// Free (OFL) fonts bundled in res/font; licences in docs/third-party.md.

/** Heavy italic display face, the closest free match to the cover logo. */
val DisplayFamily = FontFamily(Font(R.font.shrikhand_regular))

/** Condensed grotesque of sixties technical brochures, for body and labels. */
val BrochureFamily = FontFamily(
    Font(R.font.barlow_semi_condensed_regular, FontWeight.Normal),
    Font(R.font.barlow_semi_condensed_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.barlow_semi_condensed_semibold, FontWeight.SemiBold),
)

/** Teletype monospace, for logs, figures and the ship's sheet. */
val TeletypeFamily = FontFamily(
    Font(R.font.space_mono_regular, FontWeight.Normal),
    Font(R.font.space_mono_bold, FontWeight.Bold),
)

val StarSmugglerTypography = Typography(
    displayLarge = TextStyle(fontFamily = DisplayFamily, fontSize = 52.sp, lineHeight = 52.sp, letterSpacing = (-0.01).em),
    displayMedium = TextStyle(fontFamily = DisplayFamily, fontSize = 40.sp, lineHeight = 42.sp),
    displaySmall = TextStyle(fontFamily = DisplayFamily, fontSize = 32.sp, lineHeight = 36.sp),
    headlineLarge = TextStyle(fontFamily = DisplayFamily, fontSize = 28.sp, lineHeight = 32.sp),
    headlineMedium = TextStyle(fontFamily = DisplayFamily, fontSize = 24.sp, lineHeight = 28.sp),
    headlineSmall = TextStyle(fontFamily = DisplayFamily, fontSize = 20.sp, lineHeight = 24.sp),
    titleLarge = TextStyle(fontFamily = BrochureFamily, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 26.sp, letterSpacing = 0.04.em),
    titleMedium = TextStyle(fontFamily = BrochureFamily, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 22.sp, letterSpacing = 0.04.em),
    titleSmall = TextStyle(fontFamily = BrochureFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 19.sp, letterSpacing = 0.06.em),
    bodyLarge = TextStyle(fontFamily = BrochureFamily, fontSize = 18.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontFamily = BrochureFamily, fontSize = 16.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = BrochureFamily, fontSize = 14.sp, lineHeight = 19.sp),
    labelLarge = TextStyle(fontFamily = BrochureFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 20.sp, letterSpacing = 0.1.em),
    labelMedium = TextStyle(fontFamily = BrochureFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 16.sp, letterSpacing = 0.1.em),
    labelSmall = TextStyle(fontFamily = BrochureFamily, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.12.em),
)

/** Teletype styles, outside the Material scale. */
object TeletypeStyles {
    val large = TextStyle(fontFamily = TeletypeFamily, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 24.sp)
    val medium = TextStyle(fontFamily = TeletypeFamily, fontSize = 14.sp, lineHeight = 20.sp)
    val small = TextStyle(fontFamily = TeletypeFamily, fontSize = 12.sp, lineHeight = 16.sp)
}
