package com.example.myapplication.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/** Brief transform feedback leaves surrounding layout still. */
@Composable
fun Modifier.pressScale(source: MutableInteractionSource, to: Float = 0.97f): Modifier {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) to else 1f,
        tween(160, easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)), label = "press")
    return graphicsLayer { scaleX = scale; scaleY = scale }
}
