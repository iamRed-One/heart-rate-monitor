package com.example.myapplication.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.myapplication.R

// Bundled fonts (SIL OFL). Variable weights need API 26+; older phones get the default instance.
@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int, weight: Int) =
    Font(res, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

val Manrope = FontFamily(
    variable(R.font.manrope, 300), variable(R.font.manrope, 400), variable(R.font.manrope, 500), variable(R.font.manrope, 600),
)




private const val MIN_LABEL_SP = 12f

fun labelTextStyle(size: TextUnit, tracking: Float = 0.12f, weight: FontWeight = FontWeight.Medium) =
    TextStyle(
        fontFamily = Manrope, fontWeight = weight, fontSize = maxOf(size.value, MIN_LABEL_SP).sp,
        letterSpacing = tracking.coerceAtMost(0.06f).em, fontFeatureSettings = "tnum",
    )

fun displayTextStyle(size: TextUnit, heading: Boolean = false, lineHeight: Float = 1.0f, tracking: TextUnit = 0.sp): TextStyle {
    val weight = when {
        heading -> FontWeight.SemiBold
        size.value >= 56f -> FontWeight.Light
        else -> FontWeight.Normal
    }
    val track = if (tracking != 0.sp) tracking else if (heading) (-0.02).em else 0.sp
    return TextStyle(
        fontFamily = Manrope, fontWeight = weight, fontSize = size, lineHeight = size * lineHeight,
        letterSpacing = track, fontFeatureSettings = "tnum",
    )
}

val HeartTypography = Typography(
    displayLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Light, fontSize = 80.sp,
        lineHeight = 86.sp, letterSpacing = (-2).sp, fontFeatureSettings = "tnum"),
    headlineMedium = displayTextStyle(28.sp, heading = true),
    headlineSmall = displayTextStyle(26.sp, heading = true),
    titleLarge = displayTextStyle(22.sp, heading = true),
    titleMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = labelTextStyle(12.sp, 0.06f),
    labelMedium = labelTextStyle(12.sp, 0.06f),
    labelSmall = labelTextStyle(12.sp, 0.06f, FontWeight.Normal),
)
