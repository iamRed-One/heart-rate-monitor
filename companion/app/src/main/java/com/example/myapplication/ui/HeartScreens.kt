package com.example.myapplication.ui

import android.animation.ValueAnimator
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.myapplication.R
import com.example.myapplication.heart.CollectionState
import com.example.myapplication.heart.MonitorState
import com.example.myapplication.sync.HeartDevice
import com.example.myapplication.ui.theme.HeartColors

@Composable
fun HeartMark(modifier: Modifier = Modifier) {
    Box(modifier.size(36.dp).background(HeartColors.surface2, CircleShape), contentAlignment = Alignment.Center) {
        Icon(Icons.Outlined.FavoriteBorder, contentDescription = null,
            modifier = Modifier.size(21.dp), tint = HeartColors.accent)
    }
}

@Composable
fun HeartArtwork(animate: Boolean, bpm: Int, modifier: Modifier = Modifier) {
    val motion = motionEnabled()
    val amplitude by animateFloatAsState(if (animate && motion) 1f else 0f,
        tween(180, easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)), label = "pulse-envelope")
    val scale = if (amplitude > 0f && motion) {
        val period = 60_000 / bpm.coerceIn(1, 300)
        val transition = rememberInfiniteTransition(label = "heart-beat")
        val value by transition.animateFloat(1f, 1f, infiniteRepeatable(keyframes {
            durationMillis = period
            1f at 0
            1.045f at (period * .12f).toInt()
            1f at (period * .25f).toInt()
            1.025f at (period * .38f).toInt()
            1f at (period * .6f).toInt()
            1f at period
        }), label = "heart-pulse")
        1f + (value - 1f) * amplitude
    } else 1f
    Image(painterResource(R.drawable.heart_anatomy), "Illustrated anatomical heart",
        modifier.graphicsLayer { scaleX = scale; scaleY = scale }, contentScale = ContentScale.Fit)
}

@Composable
private fun motionEnabled(): Boolean {
    val context = LocalContext.current
    return if (Build.VERSION.SDK_INT >= 26) ValueAnimator.areAnimatorsEnabled()
        else Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
}

@Composable
private fun BpmValue(value: Int?, fresh: Boolean) {
    val text = value?.toString() ?: "\u2014"
    val duration = if (motionEnabled()) 140 else 0
    Crossfade(targetState = text, modifier = Modifier.widthIn(min = 128.dp),
        animationSpec = tween(duration, easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)), label = "bpm-update") { number ->
        Box(contentAlignment = Alignment.Center) {
            Text(number, style = MaterialTheme.typography.displayLarge,
                color = if (fresh || value == null) HeartColors.ink else HeartColors.muted)
        }
    }
}

@Composable
fun ConnectionLabel(text: String, active: Boolean, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Box(Modifier.size(7.dp).background(if (active) HeartColors.accent else HeartColors.dim, CircleShape))
        Text(text, style = MaterialTheme.typography.labelMedium,
            color = if (active) HeartColors.accentInk else HeartColors.muted)
    }
}

@Composable
fun PageHeading(title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text(description, style = MaterialTheme.typography.bodyMedium, color = HeartColors.muted)
    }
}

@Composable
fun SignInPrompt(onSignIn: () -> Unit) {
    Surface(color = HeartColors.surface2, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Keep your readings together", style = MaterialTheme.typography.titleMedium)
            Text("Live viewing works without an account. Sign in to save and import recordings.",
                style = MaterialTheme.typography.bodyMedium, color = HeartColors.muted)
            TextButton(onClick = onSignIn) { Text("Sign in") }
        }
    }
}

@Composable
fun HomeContent(
    monitor: MonitorState, collection: CollectionState, device: HeartDevice?, now: Long,
    preview: Boolean, onDevices: () -> Unit, onRead: () -> Unit, onSave: () -> Unit,
) {
    val displayNow = maxOf(now, System.currentTimeMillis())
    val fresh = monitor.isFresh(displayNow)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Your heartbeat.", style = MaterialTheme.typography.headlineMedium)
            ConnectionLabel(if (preview) "Sample data" else if (monitor.connected) "Connected" else "Not connected",
                monitor.connected)
        }
        Text(if (preview) "Design preview. No watch or server is connected."
            else "Take a reading when you are ready.",
            color = HeartColors.muted, style = MaterialTheme.typography.bodyMedium)
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HeartArtwork(monitor.active && monitor.connected, monitor.bpm ?: 72, Modifier.fillMaxWidth().height(214.dp))
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BpmValue(monitor.bpm, fresh)
                Text("BPM", Modifier.padding(bottom = 14.dp), style = MaterialTheme.typography.titleSmall,
                    color = HeartColors.muted)
            }
            Text(if (preview) "Sample measurement" else monitor.message,
                color = if (monitor.active) HeartColors.accentInk else HeartColors.muted,
                style = MaterialTheme.typography.bodyMedium)
            if (monitor.bpm != null) Text(if (fresh) "Measured just now" else
                "Last reading \u00b7 ${((displayNow - monitor.lastAt).coerceAtLeast(0) / 1000)}s ago",
                color = HeartColors.dim, style = MaterialTheme.typography.bodySmall)
        }
        Surface(color = HeartColors.surface, shape = RoundedCornerShape(16.dp)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(HeartColors.surface2),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Watch, null, tint = HeartColors.accentInk)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(device?.model ?: "Your watch", style = MaterialTheme.typography.titleSmall)
                    Text(if (monitor.connected) device?.battery?.let { "$it% battery" } ?: "Bluetooth connected"
                        else "Choose a supported wearable", color = HeartColors.muted,
                        style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = onDevices) { Text(if (monitor.connected) "Manage" else "Connect") }
            }
        }
        HeartButton(when {
            collection.active -> "Stop recording"
            monitor.active -> "Stop reading"
            monitor.connected -> "Read heart rate"
            else -> "Connect a watch"
        }, if (monitor.connected || monitor.active || collection.active) onRead else onDevices,
            modifier = Modifier.fillMaxWidth())
        if (monitor.connected && !collection.active) OutlinedButton(onClick = onSave,
            modifier = Modifier.fillMaxWidth(), enabled = !monitor.active) { Text("Save a recording") }
        Text(if (collection.active) "${collection.captured} captured \u00b7 ${collection.sent} uploaded"
            else "A normal read stops after a result. Recording keeps the sensor on until you stop.",
            style = MaterialTheme.typography.bodySmall, color = HeartColors.dim)
        if (collection.active || collection.recordingId != null || collection.message != "Ready to record") Text(collection.message,
            style = MaterialTheme.typography.bodySmall, color = HeartColors.muted)
    }
}

@Composable
fun ThemeToggle(darkTheme: Boolean, onToggle: () -> Unit) {
    IconButton(onClick = onToggle) {
        Icon(if (darkTheme) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
            contentDescription = if (darkTheme) "Switch to light mode" else "Switch to dark mode",
            tint = HeartColors.ink)
    }
}
