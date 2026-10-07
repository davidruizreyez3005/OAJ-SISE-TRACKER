package mx.sisetracker.ui.theme

import androidx.compose.ui.graphics.Color

// Every app color lives in this file so values can be swapped in later. The
// palette follows the "Visual design" table in CLAUDE.md: Mexican
// institutional guinda (burgundy) and dorado (gold), on warm ivory neutrals.
// Primary is guinda #691C32, secondary a dark gold for text-bearing accents,
// tertiary the institutional gold #BC955C (with dark text: "Nuevo" badges).
// Error stays the OAJ stylesheet's red #B31217. The rest (containers, inverse
// and surface-container tones) are derived in the same warm family so
// Material never falls back to its default purple tints.
// ThemeContrastTest checks the text pairs against WCAG AA (4.5:1).

// Light scheme
val PrimaryLight = Color(0xFF691C32)
val OnPrimaryLight = Color(0xFFFFFFFF)
val PrimaryContainerLight = Color(0xFFF5DCE2)
val OnPrimaryContainerLight = Color(0xFF3D0A1A)
val InversePrimaryLight = Color(0xFFF0B5C3)
val SecondaryLight = Color(0xFF7A5C1E)
val OnSecondaryLight = Color(0xFFFFFFFF)
val SecondaryContainerLight = Color(0xFFF1E4C3)
val OnSecondaryContainerLight = Color(0xFF2A1E05)
val TertiaryLight = Color(0xFFBC955C)
val OnTertiaryLight = Color(0xFF2A1E05)
val TertiaryContainerLight = Color(0xFFF6EBD3)
val OnTertiaryContainerLight = Color(0xFF3A2A08)
val BackgroundLight = Color(0xFFFBF8F4)
val OnBackgroundLight = Color(0xFF2B2626)
val SurfaceLight = Color(0xFFFBF8F4)
val OnSurfaceLight = Color(0xFF2B2626)
val SurfaceVariantLight = Color(0xFFEFE6E2)
val OnSurfaceVariantLight = Color(0xFF534448)
val SurfaceDimLight = Color(0xFFE2D8D4)
val SurfaceBrightLight = Color(0xFFFBF8F4)
val SurfaceContainerLowestLight = Color(0xFFFFFFFF)
val SurfaceContainerLowLight = Color(0xFFF6F1EE)
val SurfaceContainerLight = Color(0xFFF1EBE8)
val SurfaceContainerHighLight = Color(0xFFECE5E2)
val SurfaceContainerHighestLight = Color(0xFFE6DFDC)
val InverseSurfaceLight = Color(0xFF352F30)
val InverseOnSurfaceLight = Color(0xFFF8EEEF)
val OutlineLight = Color(0xFF85737A)
val OutlineVariantLight = Color(0xFFD8C2C7)
val ErrorLight = Color(0xFFB31217)
val OnErrorLight = Color(0xFFFFFFFF)
val ErrorContainerLight = Color(0xFFF9DEDC)
val OnErrorContainerLight = Color(0xFF410E0B)
val ScrimLight = Color(0xFF000000)

// Dark scheme
val PrimaryDark = Color(0xFFF0B5C3)
val OnPrimaryDark = Color(0xFF561D2E)
val PrimaryContainerDark = Color(0xFF7F2A42)
val OnPrimaryContainerDark = Color(0xFFFFD9E1)
val InversePrimaryDark = Color(0xFF691C32)
val SecondaryDark = Color(0xFFE3C487)
val OnSecondaryDark = Color(0xFF3E2E00)
val SecondaryContainerDark = Color(0xFF5A4415)
val OnSecondaryContainerDark = Color(0xFFF7E2B5)
val TertiaryDark = Color(0xFFD9B77E)
val OnTertiaryDark = Color(0xFF3A2A08)
val TertiaryContainerDark = Color(0xFF5B4518)
val OnTertiaryContainerDark = Color(0xFFF6E1B9)
val BackgroundDark = Color(0xFF1B1416)
val OnBackgroundDark = Color(0xFFEDE0E2)
val SurfaceDark = Color(0xFF1B1416)
val OnSurfaceDark = Color(0xFFEDE0E2)
val SurfaceVariantDark = Color(0xFF3B2F32)
val OnSurfaceVariantDark = Color(0xFFD8C2C7)
val SurfaceDimDark = Color(0xFF1B1416)
val SurfaceBrightDark = Color(0xFF42373A)
val SurfaceContainerLowestDark = Color(0xFF140E10)
val SurfaceContainerLowDark = Color(0xFF231B1D)
val SurfaceContainerDark = Color(0xFF281F22)
val SurfaceContainerHighDark = Color(0xFF33292C)
val SurfaceContainerHighestDark = Color(0xFF3E3437)
val InverseSurfaceDark = Color(0xFFEDE0E2)
val InverseOnSurfaceDark = Color(0xFF352F30)
val OutlineDark = Color(0xFFA08C91)
val OutlineVariantDark = Color(0xFF534448)
val ErrorDark = Color(0xFFF2B8B5)
val OnErrorDark = Color(0xFF601410)
val ErrorContainerDark = Color(0xFF8C1D18)
val OnErrorContainerDark = Color(0xFFF9DEDC)
val ScrimDark = Color(0xFF000000)

// Fixed accents: the same in both schemes, by Material's definition.
val PrimaryFixed = Color(0xFFFFD9E1)
val PrimaryFixedDim = Color(0xFFF0B5C3)
val OnPrimaryFixed = Color(0xFF3D0A1A)
val OnPrimaryFixedVariant = Color(0xFF7F2A42)
val SecondaryFixed = Color(0xFFF7E2B5)
val SecondaryFixedDim = Color(0xFFE3C487)
val OnSecondaryFixed = Color(0xFF261A00)
val OnSecondaryFixedVariant = Color(0xFF5A4415)
val TertiaryFixed = Color(0xFFF6E1B9)
val TertiaryFixedDim = Color(0xFFD9B77E)
val OnTertiaryFixed = Color(0xFF261A00)
val OnTertiaryFixedVariant = Color(0xFF5B4518)

// Brand chrome: the top app bar (deep guinda in both schemes, so the dark
// theme doesn't get a pale pink bar) and the gold rule under it.
val TopBarLight = Color(0xFF691C32)
val TopBarDark = Color(0xFF4A1424)
val OnTopBar = Color(0xFFFFFFFF)
val GoldRuleLight = Color(0xFFBC955C)
val GoldRuleDark = Color(0xFFD9B77E)
