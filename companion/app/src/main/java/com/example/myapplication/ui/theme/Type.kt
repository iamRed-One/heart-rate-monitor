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

val Sans = Manrope


private const val MIN_LABEL_SP = 12f

fun mono(size: TextUnit, tracking: Float = 0.12f, weight: FontWeight = FontWeight.Medium) =
    TextStyle(
        fontFamily = Manrope, fontWeight = weight, fontSize = maxOf(size.value, MIN_LABEL_SP).sp,
        letterSpacing = tracking.coerceAtMost(0.06f).em, fontFeatureSettings = "tnum",
    )

fun serif(size: TextUnit, italic: Boolean = false, lineHeight: Float = 1.0f, tracking: TextUnit = 0.sp): TextStyle {
    val weight = when {
        italic -> FontWeight.SemiBold
        size.value >= 56f -> FontWeight.Light
        else -> FontWeight.Normal
    }
    val track = if (tracking != 0.sp) tracking else if (italic) (-0.02).em else 0.sp
    return TextStyle(
        fontFamily = Manrope, fontWeight = weight, fontSize = size, lineHeight = size * lineHeight,
        letterSpacing = track, fontFeatureSettings = "tnum",
    )
}

val Typography = Typography(
    headlineMedium = serif(34.sp, italic = true),
    headlineSmall = serif(26.sp, italic = true),
    titleLarge = serif(24.sp, italic = true),
    titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = mono(12.sp, 0.06f),
    labelMedium = mono(12.sp, 0.06f),
    labelSmall = mono(12.sp, 0.06f, FontWeight.Normal),
)

val NumberStyle = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")
