package com.afterlater.core.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.afterlater.core.model.AppTheme

val Ink = Color(0xFF101110)
val Elevated = Color(0xFF181A18)
val Raised = Color(0xFF1E211D)
val Hairline = Color(0xFF30342F)
val HairlineStrong = Color(0xFF4A4F46)
val Paper = Color(0xFFF1F0E9)
val Muted = Color(0xFF9A9E96)
val Dim = Color(0xFF6C7069)
val Acid = Color(0xFFC5F56A)
val AcidDeep = Color(0xFF9ACB43)
val Danger = Color(0xFFFF7A66)
val TrueBlack = Color(0xFF000000)

@Immutable
data class AfterLaterColors(
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val border: Color,
    val borderStrong: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textDim: Color,
    val accent: Color,
    val accentInk: Color,
    val danger: Color,
    val scrim: Color,
)

val InkPalette = AfterLaterColors(
    background = Ink,
    surface = Elevated,
    surfaceRaised = Raised,
    border = Hairline,
    borderStrong = HairlineStrong,
    textPrimary = Paper,
    textSecondary = Muted,
    textDim = Dim,
    accent = Acid,
    accentInk = Ink,
    danger = Danger,
    scrim = Color(0xE6101110),
)

val BlackoutPalette = InkPalette.copy(
    background = TrueBlack,
    surface = Color(0xFF0B0C0B),
    surfaceRaised = Color(0xFF121412),
)

fun paletteFor(theme: AppTheme): AfterLaterColors = when (theme) {
    AppTheme.INK -> InkPalette
    AppTheme.BLACKOUT -> BlackoutPalette
}

val LocalAfterLaterColors = staticCompositionLocalOf { InkPalette }
