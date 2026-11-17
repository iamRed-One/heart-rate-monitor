package com.example.myapplication.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class NightColors(
    val dark: Boolean,
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val line: Color,
    val line2: Color,
    val track: Color,
    val ink: Color,
    val muted: Color,
    val dim: Color,
    val faint: Color,
    val accent: Color,
    val onAccent: Color,
    val accentInk: Color,
    val accentDim: Color,
    val lavender: Color,
    val lavenderInk: Color,
    val coral: Color,
    val coralInk: Color,
    val navBg: Color,
)

val NightDark = NightColors(
    dark = true,
    bg = Color(0xFF0C0B10), surface = Color(0xFF17151D), surface2 = Color(0xFF1E1C25),
    line = Color(0xFF2E2A36), line2 = Color(0xFF3A3644), track = Color(0xFF2A2733),
    ink = Color(0xFFF4F1EA), muted = Color(0xFFA9A4B0), dim = Color(0xFF8C8794), faint = Color(0xFF5E5966),
    accent = Color(0xFFD4FF3A), onAccent = Color(0xFF0C0B10), accentInk = Color(0xFFD4FF3A), accentDim = Color(0xFF5A6B1A),
    lavender = Color(0xFFB8A6FF), lavenderInk = Color(0xFFB8A6FF),
    coral = Color(0xFFFF6B4A), coralInk = Color(0xFFFF6B4A),
    navBg = Color(0xDB1C1A22),
)

val NightLight = NightColors(
    dark = false,
    bg = Color(0xFFFFFFFF), surface = Color(0xFFFFFFFF), surface2 = Color(0xFFEDF2ED),
    line = Color(0xFFD9E1DA), line2 = Color(0xFFBCCBBF), track = Color(0xFFE4DFD4),
    ink = Color(0xFF0C0B10), muted = Color(0xFF4A4652), dim = Color(0xFF6B6672), faint = Color(0xFF8C8794),
    accent = Color(0xFF245744), onAccent = Color(0xFFFFFFFF), accentInk = Color(0xFF173E32), accentDim = Color(0xFFB9CC6A),
    lavender = Color(0xFFB8A6FF), lavenderInk = Color(0xFF5B4BC4),
    coral = Color(0xFFFF6B4A), coralInk = Color(0xFFC2361A),
    navBg = Color(0xE6FFFFFF),
)

val LocalNight = staticCompositionLocalOf { NightDark }

object Night {
    val c: NightColors @Composable get() = LocalNight.current
}

@Immutable
data class StatusColors(val ok: Color, val warn: Color, val bad: Color, val idle: Color)

val LocalStatusColors = staticCompositionLocalOf { StatusColors(Color.Green, Color.Yellow, Color.Red, Color.Gray) }

object AppTheme {
    val status: StatusColors @Composable get() = LocalStatusColors.current
}

@Composable
fun MyApplicationTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    val n = if (darkTheme) NightDark else NightLight
    val scheme = if (darkTheme) darkColorScheme(
        primary = n.accent, onPrimary = n.onAccent, primaryContainer = n.surface2, onPrimaryContainer = n.ink,
        secondary = n.lavender, background = n.bg, onBackground = n.ink, surface = n.surface, onSurface = n.ink,
        surfaceVariant = n.surface2, onSurfaceVariant = n.muted, surfaceContainer = n.surface, surfaceContainerHigh = n.surface2,
        surfaceContainerHighest = n.surface2, outline = n.line2, outlineVariant = n.line, error = n.coral,
    ) else lightColorScheme(
        primary = n.accent, onPrimary = n.onAccent, primaryContainer = n.surface2, onPrimaryContainer = n.ink,
        secondary = n.accentInk, secondaryContainer = n.surface2, onSecondaryContainer = n.accentInk, background = n.bg, onBackground = n.ink, surface = n.surface, onSurface = n.ink,
        surfaceVariant = n.surface2, onSurfaceVariant = n.muted, surfaceContainer = n.surface, surfaceContainerHigh = n.surface2,
        surfaceContainerHighest = n.surface2, outline = n.line2, outlineVariant = n.line, error = n.coralInk,
    )
    val status = StatusColors(ok = n.accentInk, warn = n.coralInk, bad = n.coralInk, idle = n.dim)
    CompositionLocalProvider(LocalNight provides n, LocalStatusColors provides status) {
        MaterialTheme(colorScheme = scheme, typography = Typography, content = content)
    }
}
