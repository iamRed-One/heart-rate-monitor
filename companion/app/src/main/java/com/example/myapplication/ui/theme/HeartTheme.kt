package com.example.myapplication.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

/** Semantic roles shared by custom controls and Material components. */
object HeartColors {
    val bg: Color @Composable get() = MaterialTheme.colorScheme.background
    val surface: Color @Composable get() = MaterialTheme.colorScheme.surface
    val surface2: Color @Composable get() = MaterialTheme.colorScheme.surfaceVariant
    val line: Color @Composable get() = MaterialTheme.colorScheme.outlineVariant
    val line2: Color @Composable get() = MaterialTheme.colorScheme.outline
    val ink: Color @Composable get() = MaterialTheme.colorScheme.onBackground
    val muted: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
    val dim: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
    val accent: Color @Composable get() = MaterialTheme.colorScheme.primary
    val onAccent: Color @Composable get() = MaterialTheme.colorScheme.onPrimary
    val accentInk: Color @Composable get() = MaterialTheme.colorScheme.secondary
    val coralInk: Color @Composable get() = MaterialTheme.colorScheme.error
}

private val LightColors = lightColorScheme(
    primary = Color(0xFFBC2341), onPrimary = Color.White,
    primaryContainer = Color(0xFFF8E9ED), onPrimaryContainer = Color(0xFF281C22),
    secondary = Color(0xFF9E1C38), onSecondary = Color.White,
    secondaryContainer = Color(0xFFF8E9ED), onSecondaryContainer = Color(0xFF9E1C38),
    background = Color(0xFFFCF8F9), onBackground = Color(0xFF281C22),
    surface = Color.White, onSurface = Color(0xFF281C22),
    surfaceVariant = Color(0xFFF8E9ED), onSurfaceVariant = Color(0xFF6E5D65),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFCF8F9),
    surfaceContainer = Color.White, surfaceContainerHigh = Color(0xFFF8E9ED),
    surfaceContainerHighest = Color(0xFFF8E9ED), surfaceTint = Color(0xFFBC2341),
    outline = Color(0xFFD7BDC6), outlineVariant = Color(0xFFEADDE1),
    error = Color(0xFFC2361A), onError = Color.White,
    errorContainer = Color(0xFFFFDAD2), onErrorContainer = Color(0xFF410900),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF8FA3), onPrimary = Color(0xFF480A1B),
    primaryContainer = Color(0xFF542332), onPrimaryContainer = Color(0xFFFFD9E1),
    secondary = Color(0xFFFFA9BA), onSecondary = Color(0xFF480A1B),
    secondaryContainer = Color(0xFF48232E), onSecondaryContainer = Color(0xFFFFBDCB),
    background = Color(0xFF151114), onBackground = Color(0xFFF6EAF0),
    surface = Color(0xFF211B20), onSurface = Color(0xFFF6EAF0),
    surfaceVariant = Color(0xFF33252D), onSurfaceVariant = Color(0xFFCFBAC4),
    surfaceContainerLowest = Color(0xFF100D10), surfaceContainerLow = Color(0xFF1B161A),
    surfaceContainer = Color(0xFF211B20), surfaceContainerHigh = Color(0xFF2B2229),
    surfaceContainerHighest = Color(0xFF372B33), surfaceTint = Color(0xFFFF8FA3),
    outline = Color(0xFF9B848F), outlineVariant = Color(0xFF51404A),
    error = Color(0xFFFFB4A5), onError = Color(0xFF690F00),
    errorContainer = Color(0xFF8D210B), onErrorContainer = Color(0xFFFFDAD2),
)

@Composable
fun HeartTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, typography = HeartTypography) {
        CompositionLocalProvider(LocalContentColor provides colors.onBackground, content = content)
    }
}
