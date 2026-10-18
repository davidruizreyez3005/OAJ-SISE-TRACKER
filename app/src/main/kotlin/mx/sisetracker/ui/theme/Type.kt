package mx.sisetracker.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    // Long síntesis read more easily with a little more leading.
    bodyLarge = base.bodyLarge.copy(lineHeight = 26.sp),
)

/** Softer corners than Material's defaults: cards 16 dp, sheets and header cards 24 dp. */
val SiseShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
