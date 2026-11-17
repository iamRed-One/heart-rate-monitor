package com.example.myapplication.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** The light palette used by every HeaRt screen. */
object HeartColors {
    val bg = Color(0xFFFFFFFF)
    val surface = Color(0xFFFFFFFF)
    val surface2 = Color(0xFFEDF2ED)
    val line = Color(0xFFD9E1DA)
    val line2 = Color(0xFFBCCBBF)
    val ink = Color(0xFF0C0B10)
    val muted = Color(0xFF4A4652)
    val dim = Color(0xFF6B6672)
    val accent = Color(0xFF245744)
    val onAccent = Color(0xFFFFFFFF)
    val accentInk = Color(0xFF173E32)
    val coralInk = Color(0xFFC2361A)
}

@Composable
fun HeartTheme(content: @Composable () -> Unit) {
    val n = HeartColors
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = n.accent, onPrimary = n.onAccent,
            primaryContainer = n.surface2, onPrimaryContainer = n.ink,
            secondary = n.accentInk, secondaryContainer = n.surface2,
            onSecondaryContainer = n.accentInk,
            background = n.bg, onBackground = n.ink,
            surface = n.surface, onSurface = n.ink,
            surfaceVariant = n.surface2, onSurfaceVariant = n.muted,
            surfaceContainer = n.surface, surfaceContainerHigh = n.surface2,
            surfaceContainerHighest = n.surface2,
            outline = n.line2, outlineVariant = n.line, error = n.coralInk,
        ),
        typography = HeartTypography,
        content = content,
    )
}
