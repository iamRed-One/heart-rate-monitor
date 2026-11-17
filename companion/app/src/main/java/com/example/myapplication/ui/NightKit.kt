package com.example.myapplication.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.ui.theme.Night
import com.example.myapplication.ui.theme.mono


enum class NBtn { Accent, Outline, Ink, Solid }
@Composable
fun NButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: NBtn = NBtn.Outline,
    height: Dp = 48.dp,
    enabled: Boolean = true,
    textStyle: TextStyle? = null,
    fill: Float = 0f,
    loading: Boolean = false,
) {
    val n = Night.c
    // Disabled filled buttons fade rather than vanish; a loading button keeps its colour so the
    // spinner reads as "working on it", not "unavailable".
    val dim = !enabled && !loading
    val (bg, fg, border) = when (style) {
        NBtn.Accent -> Triple(if (dim) n.accent.copy(alpha = 0.4f) else n.accent, n.onAccent, Color.Transparent)
        NBtn.Outline -> Triple(Color.Transparent, if (dim) n.faint else n.ink, n.line2)
        NBtn.Ink -> Triple(if (dim) n.ink.copy(alpha = 0.4f) else n.ink, n.bg, Color.Transparent)
        NBtn.Solid -> Triple(n.onAccent, Color(0xFFF4F1EA), Color.Transparent)
    }
    val source = remember { MutableInteractionSource() }
    Box(
        modifier.height(height).pressScale(source, 0.96f).clip(RoundedCornerShape(10.dp)).background(bg)
            .border(1.dp, border, RoundedCornerShape(10.dp))
            .drawBehind { if (fill > 0f) drawRect(n.accent, size = size.copy(width = size.width * fill.coerceIn(0f, 1f))) }
            .clickable(interactionSource = source, indication = null, enabled = enabled && !loading, role = Role.Button, onClick = onClick)
            .semantics { if (loading) stateDescription = "In progress" }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        val color = if (fill > 0.5f) n.onAccent else fg
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (loading) CircularProgressIndicator(Modifier.size(14.dp), color = color, strokeWidth = 2.dp, trackColor = color.copy(alpha = 0.2f))
            Text(
                text, style = textStyle ?: mono(12.sp, 0.12f),
                color = color, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}


@Composable
fun Hairline(modifier: Modifier = Modifier) = Box(modifier.fillMaxWidth().height(1.dp).background(Night.c.line))
