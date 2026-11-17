package com.example.myapplication.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.ui.theme.HeartColors
import com.example.myapplication.ui.theme.labelTextStyle

@Composable
fun HeartButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val n = HeartColors
    val background = if (!enabled && !loading) n.accent.copy(alpha = 0.4f) else n.accent
    val source = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier.height(48.dp).pressScale(source, 0.96f).clip(shape).background(background)
            .border(1.dp, Color.Transparent, shape)
            .clickable(interactionSource = source, indication = null, enabled = enabled && !loading,
                role = Role.Button, onClick = onClick)
            .semantics { if (loading) stateDescription = "In progress" }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (loading) CircularProgressIndicator(Modifier.size(14.dp), color = n.onAccent,
                strokeWidth = 2.dp, trackColor = n.onAccent.copy(alpha = 0.2f))
            Text(text, style = labelTextStyle(12.sp, 0.12f), color = n.onAccent,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun Hairline(modifier: Modifier = Modifier) =
    Box(modifier.fillMaxWidth().height(1.dp).background(HeartColors.line))
