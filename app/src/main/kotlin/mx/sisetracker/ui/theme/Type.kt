package mx.sisetracker.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

private val base = Typography()

/**
 * Material's type scale, with the system serif (Noto Serif on Android) for
 * display, headline and large titles: the formal look of legal documents,
 * while body text and labels stay in the sans-serif for small-size legibility.
 * No bundled fonts, so nothing is downloaded.
 */
val SiseTypography = base.copy(
    displayLarge = base.displayLarge.copy(fontFamily = FontFamily.Serif),
    displayMedium = base.displayMedium.copy(fontFamily = FontFamily.Serif),
    displaySmall = base.displaySmall.copy(fontFamily = FontFamily.Serif),
    headlineLarge = base.headlineLarge.copy(fontFamily = FontFamily.Serif),
    headlineMedium = base.headlineMedium.copy(fontFamily = FontFamily.Serif),
    headlineSmall = base.headlineSmall.copy(fontFamily = FontFamily.Serif),
    titleLarge = base.titleLarge.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
)
