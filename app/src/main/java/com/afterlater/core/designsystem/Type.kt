package com.afterlater.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.afterlater.R

val DisplayFamily = FontFamily(Font(R.font.archivo_black, FontWeight.Black))

val BodyFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

val MonoFamily = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
)

object AfterType {
    val DisplayHero = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.Black,
        fontSize = 46.sp,
        lineHeight = 44.sp,
        letterSpacing = (-1.2).sp,
    )
    val DisplayLarge = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.Black,
        fontSize = 36.sp,
        lineHeight = 37.sp,
        letterSpacing = (-0.8).sp,
    )
    val DisplayMedium = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.Black,
        fontSize = 27.sp,
        lineHeight = 29.sp,
        letterSpacing = (-0.5).sp,
    )
    val DisplaySmall = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.Black,
        fontSize = 20.sp,
        lineHeight = 23.sp,
        letterSpacing = (-0.2).sp,
    )
    val Wordmark = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.Black,
        fontSize = 21.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.2.sp,
    )
    val BodyLarge = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    )
    val Body = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    )
    val BodySmall = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.5.sp,
        lineHeight = 18.sp,
    )
    val BodyMedium = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    )
    val Mono = TextStyle(
        fontFamily = MonoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 10.5.sp,
        lineHeight = 15.sp,
        letterSpacing = 1.1.sp,
    )
    val MonoSmall = TextStyle(
        fontFamily = MonoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 9.sp,
        lineHeight = 13.sp,
        letterSpacing = 1.3.sp,
    )
    val MonoMedium = TextStyle(
        fontFamily = MonoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 1.2.sp,
    )
    val MonoNumeric = TextStyle(
        fontFamily = MonoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    )
    val Button = TextStyle(
        fontFamily = MonoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 10.5.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.6.sp,
    )
}

val AfterTypography = Typography(
    displayLarge = AfterType.DisplayHero,
    displayMedium = AfterType.DisplayLarge,
    displaySmall = AfterType.DisplayMedium,
    headlineLarge = AfterType.DisplayLarge,
    headlineMedium = AfterType.DisplayMedium,
    headlineSmall = AfterType.DisplaySmall,
    titleLarge = AfterType.DisplaySmall,
    titleMedium = AfterType.BodyMedium,
    bodyLarge = AfterType.BodyLarge,
    bodyMedium = AfterType.Body,
    bodySmall = AfterType.BodySmall,
    labelLarge = AfterType.Button,
    labelMedium = AfterType.Mono,
    labelSmall = AfterType.MonoSmall,
)
