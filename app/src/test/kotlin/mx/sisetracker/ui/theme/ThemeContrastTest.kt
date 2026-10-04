package mx.sisetracker.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every text-on-background pair in both schemes meets WCAG AA (4.5:1). */
class ThemeContrastTest {

    @Test
    fun `light scheme meets WCAG AA for body text`() = assertAa(LightColors)

    @Test
    fun `dark scheme meets WCAG AA for body text`() = assertAa(DarkColors)

    private fun assertAa(scheme: ColorScheme) {
        val pairs = with(scheme) {
            mapOf(
                "primary / onPrimary" to (primary to onPrimary),
                "primaryContainer / onPrimaryContainer" to (primaryContainer to onPrimaryContainer),
                "secondary / onSecondary" to (secondary to onSecondary),
                "secondaryContainer / onSecondaryContainer" to (secondaryContainer to onSecondaryContainer),
                "tertiary / onTertiary" to (tertiary to onTertiary),
                "tertiaryContainer / onTertiaryContainer" to (tertiaryContainer to onTertiaryContainer),
                "error / onError" to (error to onError),
                "errorContainer / onErrorContainer" to (errorContainer to onErrorContainer),
                "background / onBackground" to (background to onBackground),
                "background / primary" to (background to primary),
                "surface / onSurface" to (surface to onSurface),
                "surface / onSurfaceVariant" to (surface to onSurfaceVariant),
                "surfaceVariant / onSurfaceVariant" to (surfaceVariant to onSurfaceVariant),
                "surfaceContainerLowest / onSurface" to (surfaceContainerLowest to onSurface),
                "surfaceContainerLow / onSurface" to (surfaceContainerLow to onSurface),
                "surfaceContainer / onSurface" to (surfaceContainer to onSurface),
                "surfaceContainerHigh / onSurface" to (surfaceContainerHigh to onSurface),
                "surfaceContainerHighest / onSurface" to (surfaceContainerHighest to onSurface),
                "surfaceContainerHighest / onSurfaceVariant" to (surfaceContainerHighest to onSurfaceVariant),
                "inverseSurface / inverseOnSurface" to (inverseSurface to inverseOnSurface),
                "primaryFixed / onPrimaryFixed" to (primaryFixed to onPrimaryFixed),
                "primaryFixed / onPrimaryFixedVariant" to (primaryFixed to onPrimaryFixedVariant),
                "secondaryFixed / onSecondaryFixed" to (secondaryFixed to onSecondaryFixed),
                "secondaryFixed / onSecondaryFixedVariant" to (secondaryFixed to onSecondaryFixedVariant),
                "tertiaryFixed / onTertiaryFixed" to (tertiaryFixed to onTertiaryFixed),
                "tertiaryFixed / onTertiaryFixedVariant" to (tertiaryFixed to onTertiaryFixedVariant),
            )
        }
        val failures = pairs
            .mapValues { (_, colors) -> contrast(colors.first, colors.second) }
            .filterValues { it < 4.5 }
        assertTrue("Below 4.5:1: $failures", failures.isEmpty())
    }

    private fun contrast(a: Color, b: Color): Double {
        val la = a.luminance().toDouble()
        val lb = b.luminance().toDouble()
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }
}
