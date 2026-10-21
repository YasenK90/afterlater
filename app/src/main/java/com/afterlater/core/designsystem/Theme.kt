package com.afterlater.core.designsystem

import android.provider.Settings
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.afterlater.core.model.AppTheme

val LocalReducedMotion = staticCompositionLocalOf { false }

fun motionMillis(default: Int, reduced: Boolean): Int = if (reduced) 0 else default

@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
}

@Composable
fun AfterLaterTheme(
    theme: AppTheme = AppTheme.INK,
    content: @Composable () -> Unit,
) {
    val palette = paletteFor(theme)
    val reducedMotion = rememberReducedMotion()
    val scheme = darkColorScheme(
        primary = palette.accent,
        onPrimary = palette.accentInk,
        secondary = palette.textPrimary,
        onSecondary = palette.background,
        tertiary = palette.accent,
        background = palette.background,
        onBackground = palette.textPrimary,
        surface = palette.surface,
        onSurface = palette.textPrimary,
        surfaceVariant = palette.surfaceRaised,
        onSurfaceVariant = palette.textSecondary,
        outline = palette.border,
        outlineVariant = palette.border,
        error = palette.danger,
        onError = palette.background,
        scrim = palette.scrim,
    )
    val shapes = Shapes(
        extraSmall = RoundedCornerShape(2.dp),
        small = RoundedCornerShape(2.dp),
        medium = RoundedCornerShape(2.dp),
        large = RoundedCornerShape(3.dp),
        extraLarge = RoundedCornerShape(4.dp),
    )

    CompositionLocalProvider(
        LocalAfterLaterColors provides palette,
        LocalReducedMotion provides reducedMotion,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = AfterTypography,
            shapes = shapes,
            content = content,
        )
    }
}
