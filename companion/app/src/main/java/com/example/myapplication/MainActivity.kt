package com.example.myapplication

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.example.myapplication.adapter.LinkState
import com.example.myapplication.core.DeviceKind
import com.example.myapplication.heart.*
import com.example.myapplication.sync.HeartDevice
import com.example.myapplication.ui.*
import com.example.myapplication.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            show(WindowInsetsCompat.Type.systemBars())
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        val preview = BuildConfig.DEBUG && intent.getBooleanExtra("preview_ui", false)
        if (!preview) HeartApi.initialize(applicationContext)
        val screen = if (preview) intent.getStringExtra("screen").orEmpty() else "home"
        val preferences = getSharedPreferences("heart-appearance", MODE_PRIVATE)
        setContent {
            val systemDark = isSystemInDarkTheme()
            var choice by remember { mutableIntStateOf(preferences.getInt("dark-mode", -1)) }
            val dark = if (choice == -1) systemDark else choice == 1
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            HeartTheme(darkTheme = dark) {
                val background = HeartColors.bg.toArgb()
                @Suppress("DEPRECATION")
                SideEffect {
                    window.statusBarColor = background
                    window.navigationBarColor = background
                    window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(background))
                }
                HeartApp(preview, screen, dark) {
                    choice = if (dark) 0 else 1
                    preferences.edit().putInt("dark-mode", choice).apply()
                }
            }
        }
    }

    @Composable
    private fun HeartApp(previewMode: Boolean, initialScreen: String, darkTheme: Boolean, onToggleTheme: () -> Unit) {
        val engine = remember { if (previewMode) null else (application as SpikeApp).engine }
        val scope = rememberCoroutineScope()
        val account by HeartApi.account.collectAsState()
        val restoring by HeartApi.restoring.collectAsState()
        val collection = engine?.collector?.state?.collectAsState()?.value ?: CollectionState()
        var page by rememberSaveable { mutableIntStateOf(when (initialScreen) {
            "devices" -> 1; "history", "detail" -> 2; "files" -> 3; else -> 0
        }) }
        var showAccount by rememberSaveable { mutableStateOf(initialScreen in listOf("login", "register")) }
        var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
        val live = engine?.monitor?.state?.collectAsState()?.value ?: MonitorState()
        val monitor = if (previewMode) MonitorState(true, true, DeviceKind.WATCH, 72, now, "Design preview") else live
        val scans = engine?.scanResults?.collectAsState()?.value.orEmpty()
        val scanning = engine?.scanning?.collectAsState()?.value ?: false
        val devices = if (previewMode) listOf(HeartDevice("preview", DeviceKind.WATCH, "Demo watch", 82))
            else engine?.devices?.collectAsState()?.value.orEmpty()
        val watch = engine?.watchState?.collectAsState()?.value ?: LinkState.IDLE
        val ring = engine?.ringState?.collectAsState()?.value ?: LinkState.IDLE
        val standard = engine?.standardState?.collectAsState()?.value ?: LinkState.IDLE
        var message by remember { mutableStateOf("") }
        var working by remember { mutableStateOf(false) }
        var records by remember { mutableStateOf(if (previewMode) demoRecordings() else emptyList()) }
        var rows by remember { mutableStateOf(if (previewMode && initialScreen == "detail") demoRows() else emptyList()) }
        var selected by remember { mutableStateOf<String?>(if (previewMode && initialScreen == "detail") "preview" else null) }
        var cursor by remember { mutableStateOf<String?>(null) }
        var confirmDelete by remember { mutableStateOf<JSONObject?>(null) }
        var label by rememberSaveable { mutableStateOf("Imported recording") }
        var synthetic by rememberSaveable { mutableStateOf(false) }
        var file by remember { mutableStateOf<ByteArray?>(null) }
        var importPreview by remember { mutableStateOf<JSONObject?>(null) }
        var pairingCode by rememberSaveable { mutableStateOf("") }
        var showPairing by rememberSaveable { mutableStateOf(false) }

        fun localAction(action: () -> Unit) {
            if (previewMode) { message = "Design preview: no Bluetooth or server connection."; return }
            try { action() } catch (error: Exception) { message = error.friendly() }
        }
        fun loadRecordings() {
            if (previewMode) { records = demoRecordings(); return }
            if (account.id.isEmpty()) { records = emptyList(); return }
            scope.launch {
                working = true
                try { records = HeartApi.call("/recordings?limit=100").getJSONArray("items").objects() }
                catch (error: Exception) { message = error.friendly() }
                finally { working = false }
            }
        }
        fun loadRows(id: String, next: String? = null) {
            if (previewMode) { selected = id; rows = demoRows(); cursor = null; return }
            scope.launch {
                working = true
                try {
                    val query = next?.let { "&cursor=" + java.net.URLEncoder.encode(it, "UTF-8") }.orEmpty()
                    val result = HeartApi.call("/readings?recording_id=$id&limit=100$query")
                    rows = if (next == null) result.getJSONArray("items").objects() else rows + result.getJSONArray("items").objects()
                    cursor = result.nullable("next_cursor"); selected = id
                } catch (error: Exception) { message = error.friendly() }
                finally { working = false }
            }
        }
        val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result.filterKeys { it != Manifest.permission.POST_NOTIFICATIONS }.values.all { it })
                localAction { engine!!.startScan() }
            else message = "Allow Bluetooth access to find and connect your watch."
        }
        fun scan() {
            if (previewMode) { localAction {}; return }
            val needed = buildList {
                if (Build.VERSION.SDK_INT >= 31) { add(Manifest.permission.BLUETOOTH_SCAN); add(Manifest.permission.BLUETOOTH_CONNECT) }
                else add(Manifest.permission.ACCESS_FINE_LOCATION)
                if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            }.toTypedArray()
            if (needed.all { ContextCompat.checkSelfPermission(this@MainActivity, it) == PackageManager.PERMISSION_GRANTED })
                localAction { engine!!.startScan() }
            else permissions.launch(needed)
        }
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) scope.launch {
                working = true; message = ""
                try {
                    file = withContext(Dispatchers.IO) {
                        contentResolver.openInputStream(uri)!!.use { input ->
                            val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                            var size = input.read(buffer)
                            while (size >= 0) {
                                require(output.size() + size <= 5 * 1024 * 1024) { "Choose a file no larger than 5 MiB." }
                                output.write(buffer, 0, size); size = input.read(buffer)
                            }
                            HeartCsv.canonical(output.toByteArray())
                        }
                    }
                    message = "File selected. Review it before saving."
                } catch (error: Exception) { file = null; message = error.friendly() }
                finally { working = false }
            }
        }
        val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
            if (uri != null && account.id.isNotEmpty() && !previewMode) scope.launch {
                working = true
                try {
                    withContext(Dispatchers.IO) { contentResolver.openOutputStream(uri)!!.use { it.write(engine!!.collector.store.csv(account.id)) } }
                    message = "CSV exported."
                } catch (error: Exception) { message = error.friendly() }
                finally { working = false }
            }
        }
        fun discardImport() {
            val data = importPreview ?: return
            scope.launch {
                if (!previewMode) runCatching { HeartApi.call("/imports/" + data.getString("preview_id"), "DELETE") }
                importPreview = null
            }
        }
        LaunchedEffect(page, account.id) { message = ""; if (page == 2) loadRecordings() }
        LaunchedEffect(account.id) { if (!previewMode) { selected = null; rows = emptyList(); records = emptyList() } }
        LaunchedEffect(page) { if (page == 0) while (true) { now = System.currentTimeMillis(); delay(1000) } }
        if (showAccount) {
            AccountScreen(previewMode, initialScreen == "register", darkTheme, onToggleTheme, onDismiss = { showAccount = false },
                onSignedIn = { showAccount = false; message = "" })
            return
        }
        BackHandler(enabled = selected != null) { selected = null; rows = emptyList(); cursor = null }
        val labels = listOf("Home", "Devices", "History", "Files")
        val icons = listOf(Icons.Outlined.Home, Icons.Outlined.Watch, Icons.Outlined.Timeline, Icons.Outlined.FolderOpen)
        val selectedIcons = listOf(Icons.Filled.Home, Icons.Filled.Watch, Icons.Filled.Timeline, Icons.Filled.FolderOpen)
        Scaffold(containerColor = HeartColors.bg, topBar = {
            Column(Modifier.background(HeartColors.bg).statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    HeartMark(); Spacer(Modifier.width(10.dp))
                    Text("HeaRt", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    ThemeToggle(darkTheme, onToggleTheme)
                    if (restoring && !previewMode) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    else if (account.id.isEmpty() || previewMode) IconButton(onClick = { showAccount = true }) {
                        Icon(Icons.Outlined.AccountCircle, "Account", tint = HeartColors.ink)
                    } else TextButton(onClick = {
                        engine?.collector?.stop()
                        scope.launch { try { HeartApi.signOut() } catch (error: Exception) { message = error.friendly() } }
                    }) { Text("Sign out") }
                }
                if (previewMode) Text("Design preview \u00b7 sample data",
                    Modifier.fillMaxWidth().background(HeartColors.surface2).padding(horizontal = 20.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelMedium, color = HeartColors.accentInk)
            }
        }, bottomBar = {
            Surface(color = HeartColors.bg) {
                Column {
                    Hairline()
                    Row(Modifier.fillMaxWidth().navigationBarsPadding()) {
                        labels.forEachIndexed { index, text ->
                            Tab(selected = page == index,
                                onClick = { page = index; selected = null; rows = emptyList() },
                                modifier = Modifier.weight(1f),
                                selectedContentColor = HeartColors.accentInk,
                                unselectedContentColor = HeartColors.muted,
                                icon = { Icon(if (page == index) selectedIcons[index] else icons[index],
                                    contentDescription = null, modifier = Modifier.size(22.dp)) },
                                text = { Text(text, style = MaterialTheme.typography.labelMedium) })
                        }
                    }
                }
            }
        }) { insets ->
            LazyColumn(Modifier.fillMaxSize().padding(insets), contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)) {
                if (message.isNotEmpty()) item { Text(message, style = MaterialTheme.typography.bodyMedium, color = HeartColors.accentInk) }
                if (working) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                when (page) {
                    0 -> {
                        item {
                            HomeContent(monitor, collection, devices.firstOrNull { it.kind == monitor.kind }, now,
                                previewMode, onDevices = { page = 1 }, onRead = {
                                    if (previewMode) message = "Design preview: no sensor is active."
                                    else if (collection.active) engine!!.collector.stop()
                                    else if (monitor.active) engine!!.stopReading()
                                    else monitor.kind?.let { engine!!.readHeartRate(it) }
                                }, onSave = {
                                    if (previewMode) message = "Design preview: readings are not saved."
                                    else if (collection.active) engine!!.collector.stop()
                                    else if (account.id.isEmpty()) showAccount = true
                                    else monitor.kind?.let { engine!!.startRecording(it, pairingCode) }
                                })
                        }
                        if (account.id.isNotEmpty()) item {
                            TextButton(onClick = { showPairing = !showPairing }) { Text("Pair with the web viewer") }
                            if (showPairing) OutlinedTextField(pairingCode, { pairingCode = it.take(12) },
                                label = { Text("Web pairing code (optional)") }, singleLine = true,
                                modifier = Modifier.fillMaxWidth(), supportingText = { Text("Leave empty to start saving from this phone.") })
                        }
                    }
                    1 -> {
                        item { PageHeading("Your devices", "Connect your watch, then choose Read heart rate.") }
                        item { HeartButton(if (scanning) "Finding devices..." else "Find nearby devices", ::scan,
                            Modifier.fillMaxWidth(), enabled = !scanning && !collection.active) }
                        devices.forEach { device -> item {
                            val state = if (previewMode) LinkState.READY else when (device.kind) {
                                DeviceKind.WATCH -> watch; DeviceKind.RING -> ring; else -> standard
                            }
                            Surface(shape = RoundedCornerShape(16.dp), color = HeartColors.surface) {
                                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Outlined.Watch, null, tint = HeartColors.accent)
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(device.model, style = MaterialTheme.typography.titleMedium)
                                            Text(device.battery?.let { "$it% battery" } ?: "Battery unavailable",
                                                style = MaterialTheme.typography.bodySmall, color = HeartColors.muted)
                                        }
                                    }
                                    ConnectionLabel(if (previewMode) "Sample connection" else state.name.lowercase()
                                        .replaceFirstChar { it.uppercase() }, state == LinkState.READY)
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        OutlinedButton(onClick = { localAction { engine!!.reconnect(device.kind) } }, enabled = !collection.active) { Text("Reconnect") }
                                        TextButton(onClick = { localAction { engine!!.disconnect(device.kind) } }) { Text("Disconnect") }
                                    }
                                }
                            }
                        } }
                        if (scans.isNotEmpty()) item { Text("Nearby", style = MaterialTheme.typography.titleMedium) }
                        scans.forEach { result -> item {
                            ListItem(headlineContent = { Text(result.name) },
                                supportingContent = { Text("${result.kind.name.lowercase()} \u00b7 ${result.rssi} dBm") },
                                colors = ListItemDefaults.colors(containerColor = HeartColors.surface),
                                trailingContent = { TextButton(onClick = { localAction { engine!!.connect(result) } },
                                    enabled = !collection.active) { Text("Connect") } })
                        } }
                        if (devices.isEmpty() && scans.isEmpty() && !scanning) item {
                            Text("No watch connected yet. Keep it nearby with Bluetooth enabled.", color = HeartColors.muted)
                        }
                        item { Text("Supports ET488, Q520 and sensors using the standard Bluetooth Heart Rate service. Other watches need a compatible adapter.",
                            color = HeartColors.dim, style = MaterialTheme.typography.bodySmall) }
                    }
                    2 -> {
                        item { PageHeading("Your recordings", "Saved heart-rate readings, ready to revisit.") }
                        if (account.id.isEmpty() && !previewMode) item { SignInPrompt { showAccount = true } }
                        else {
                            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { selected = null; loadRecordings() }, enabled = !working) {
                                    Icon(Icons.Outlined.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Refresh")
                                }
                                TextButton(onClick = { localAction { engine!!.collector.retry() } }) { Text("Retry upload") }
                            } }
                            if (selected == null) {
                                if (records.isEmpty() && !working) item { Text("No recordings yet. Save a live session or import a CSV.", color = HeartColors.muted) }
                                records.forEach { record -> item {
                                    Surface(shape = RoundedCornerShape(16.dp), color = HeartColors.surface) {
                                        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Text(record.getString("label"), style = MaterialTheme.typography.titleMedium)
                                            Text("${record.getInt("count")} readings \u00b7 ${record.getString("source_type")}",
                                                color = HeartColors.muted, style = MaterialTheme.typography.bodyMedium)
                                            Text("Average ${record.optString("average_bpm", "\u2014")} BPM", color = HeartColors.accentInk,
                                                style = MaterialTheme.typography.bodyMedium)
                                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                TextButton(onClick = { loadRows(record.getString("id")) }) { Text("View readings") }
                                                TextButton(onClick = { if (previewMode) message = "Sample recordings cannot be deleted."
                                                    else confirmDelete = record }) { Text("Delete") }
                                            }
                                        }
                                    }
                                } }
                            } else {
                                item { TextButton(onClick = { selected = null; rows = emptyList(); cursor = null }) {
                                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Back to recordings")
                                } }
                                item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Captured (UTC)", style = MaterialTheme.typography.labelMedium, color = HeartColors.muted)
                                    Text("Heart rate", style = MaterialTheme.typography.labelMedium, color = HeartColors.muted)
                                }; Hairline() }
                                rows.forEach { row -> item {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(row.getString("timestamp").replace('T', ' ').take(19), style = MaterialTheme.typography.bodySmall)
                                        Text("${row.getInt("heart_rate")} BPM", style = MaterialTheme.typography.titleSmall)
                                    }; Spacer(Modifier.height(10.dp)); Hairline()
                                } }
                                if (cursor != null) item { OutlinedButton(onClick = { loadRows(selected!!, cursor) }, enabled = !working) { Text("Load more") } }
                            }
                        }
                    }
                    3 -> {
                        item { PageHeading("Bring your data", "Import a CSV or export readings saved on this phone.") }
                        item { Surface(shape = RoundedCornerShape(16.dp), color = HeartColors.surface) {
                            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Icon(Icons.Outlined.UploadFile, null, Modifier.size(32.dp), tint = HeartColors.accent)
                                Text("Import a recording", style = MaterialTheme.typography.titleMedium)
                                Text("Choose your file, review the readings, then save.", color = HeartColors.muted, style = MaterialTheme.typography.bodyMedium)
                                OutlinedButton(onClick = { picker.launch(arrayOf("text/*", "application/octet-stream")) },
                                    enabled = !working, modifier = Modifier.fillMaxWidth()) { Text(if (file == null) "Choose CSV file" else "Choose another CSV") }
                            }
                        } }
                        item { OutlinedTextField(label, { label = it.take(100) }, label = { Text("Recording name") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                        item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            FilterChip(selected = !synthetic, onClick = { synthetic = false }, label = { Text("Recorded") })
                            FilterChip(selected = synthetic, onClick = { synthetic = true }, label = { Text("Synthetic") })
                        } }
                        item { HeartButton("Review import", {
                            if (account.id.isEmpty() && !previewMode) showAccount = true
                            else scope.launch {
                                working = true
                                try { importPreview = if (previewMode) JSONObject().put("preview_id", "preview").put("count", 3)
                                    .put("start_at", "2026-11-22T09:00:00Z").put("end_at", "2026-11-22T09:02:00Z")
                                    .put("source_type", "synthetic") else HeartApi.preview(file!!, label, synthetic) }
                                catch (error: Exception) { message = error.friendly() }
                                finally { working = false }
                            }
                        }, Modifier.fillMaxWidth(), enabled = (file != null || previewMode) && label.isNotBlank() && !working) }
                        item { Text("Accepts timestamp,heart_rate CSV and compatible Vigil heart-rate exports. Up to 5 MiB and 10,000 rows. Keep full exports for their extra provenance.",
                            color = HeartColors.dim, style = MaterialTheme.typography.bodySmall) }
                        item { Hairline() }
                        item { Text("Export from this phone", style = MaterialTheme.typography.titleMedium) }
                        item { OutlinedButton(onClick = {
                            if (previewMode) message = "Design preview: no local readings to export."
                            else if (account.id.isEmpty()) showAccount = true else export.launch("HeaRt-readings.csv")
                        }, modifier = Modifier.fillMaxWidth(), enabled = !working) {
                            Icon(Icons.Outlined.FileDownload, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Export local readings")
                        } }
                        if (account.id.isEmpty() && !previewMode) item { SignInPrompt { showAccount = true } }
                    }
                }
                item { Spacer(Modifier.height(4.dp)) }
            }
        }
        confirmDelete?.let { record -> AlertDialog(onDismissRequest = { confirmDelete = null },
            title = { Text("Delete recording?") }, text = { Text("Delete ${record.getString("label")} and all its readings? This cannot be undone.") },
            confirmButton = { TextButton(onClick = { scope.launch {
                try { HeartApi.call("/recordings/" + record.getString("id"), "DELETE"); confirmDelete = null; loadRecordings() }
                catch (error: Exception) { message = error.friendly() }
            } }) { Text("Delete") } }, dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Keep recording") } }) }
        importPreview?.let { data -> AlertDialog(onDismissRequest = ::discardImport,
            title = { Text("Review your import") }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${data.getInt("count")} validated readings")
                Text(data.getString("start_at") + " to " + data.getString("end_at"))
                Text("Source: " + data.getString("source_type"))
            } }, confirmButton = { TextButton(enabled = !working, onClick = { scope.launch {
                working = true
                try {
                    if (previewMode) { message = "Design preview: no recording was saved."; importPreview = null }
                    else {
                        HeartApi.call("/imports/" + data.getString("preview_id") + "/confirm", "POST")
                        importPreview = null; file = null; message = "Recording imported."; page = 2; loadRecordings()
                    }
                } catch (error: Exception) { message = error.friendly() }
                finally { working = false }
            } }) { Text("Save recording") } }, dismissButton = { TextButton(onClick = ::discardImport) { Text("Discard") } }) }
    }

    @Composable
    private fun AccountScreen(preview: Boolean, initialRegister: Boolean, darkTheme: Boolean, onToggleTheme: () -> Unit, onDismiss: () -> Unit, onSignedIn: () -> Unit) {
        val scope = rememberCoroutineScope()
        var register by rememberSaveable { mutableStateOf(initialRegister) }
        var email by rememberSaveable { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var server by rememberSaveable { mutableStateOf(HeartApi.server) }
        var showPassword by remember { mutableStateOf(false) }
        var showServer by rememberSaveable { mutableStateOf(false) }
        var busy by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf("") }
        var notice by remember { mutableStateOf("") }
        BackHandler(onBack = onDismiss)
        LazyColumn(Modifier.fillMaxSize().background(HeartColors.bg).statusBarsPadding().navigationBarsPadding().imePadding(),
            contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                HeartMark(); Spacer(Modifier.width(10.dp)); Text("HeaRt", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                ThemeToggle(darkTheme, onToggleTheme)
                IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "Close account") }
            } }
            item { Surface(color = HeartColors.surface2, shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (register) "Closer to your heart." else "Welcome back.", style = MaterialTheme.typography.headlineMedium)
                        Text(if (register) "One account for your recordings." else "Pick up where you left off.",
                            style = MaterialTheme.typography.bodyMedium, color = HeartColors.muted)
                    }
                    HeartArtwork(false, 72, Modifier.size(100.dp))
                }
            } }
            item { Text(if (register) "Create an account" else "Sign in", style = MaterialTheme.typography.titleLarge) }
            if (notice.isNotEmpty()) item { Text(notice, color = HeartColors.accentInk) }
            item { OutlinedTextField(email, { email = it }, label = { Text("Email address") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth(), enabled = !busy) }
            item { OutlinedTextField(password, { password = it }, label = { Text("Password") }, singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = { IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        if (showPassword) "Hide password" else "Show password")
                } }, supportingText = { if (register) Text("Between 12 and 128 characters") },
                modifier = Modifier.fillMaxWidth(), enabled = !busy) }
            if (error.isNotEmpty()) item { Text(error, color = HeartColors.coralInk) }
            item { HeartButton(if (register) "Create account" else "Sign in", {
                error = ""; notice = ""
                if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) error = "Enter a valid email address."
                else if (password.isEmpty() || (register && password.length !in 12..128)) error = "Use a password between 12 and 128 characters."
                else if (preview) notice = "Design preview: no account request was sent."
                else scope.launch {
                    busy = true
                    try {
                        val parsed = server.trim().trimEnd('/').toHttpUrlOrNull()
                        require(parsed != null && parsed.encodedPath == "/" && parsed.query == null && parsed.fragment == null &&
                            (parsed.scheme == "https" || (BuildConfig.DEBUG && parsed.host in listOf("127.0.0.1", "localhost")))) {
                            "Use an HTTPS server, or the local USB address in a debug build."
                        }
                        val address = server.trim().trimEnd('/')
                        if (register) {
                            HeartApi.register(email, password, address); register = false; password = ""
                            notice = "Account created. Sign in to continue."
                        } else { HeartApi.signIn(email, password, address); password = ""; onSignedIn() }
                    } catch (failure: Exception) { error = failure.friendly() }
                    finally { busy = false }
                }
            }, Modifier.fillMaxWidth(), loading = busy, enabled = !busy) }
            item { TextButton(onClick = { register = !register; password = ""; error = ""; notice = "" }) {
                Text(if (register) "Already have an account? Sign in" else "New here? Create an account")
            } }
            item { Hairline() }
            item { Text("You can view your heart rate without an account. Sign in to save or import recordings.",
                style = MaterialTheme.typography.bodyMedium, color = HeartColors.muted) }
            item { OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Continue without an account") } }
            item { TextButton(onClick = { showServer = !showServer }) { Text("Server settings") } }
            if (showServer) item { OutlinedTextField(server, { server = it }, label = { Text("API server address") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth(), enabled = !busy) }
        }
    }

    private fun JSONArray.objects() = List(length()) { getJSONObject(it) }
    private fun JSONObject.nullable(key: String): String? = if (isNull(key)) null else optString(key).ifBlank { null }
    private fun Exception.friendly() = when (this) {
        is java.io.IOException -> "Cannot reach the server. Your live Bluetooth view is still available."
        else -> message ?: "Something went wrong. Please try again."
    }
    private fun demoRecordings() = listOf(JSONObject().put("id", "preview").put("label", "Example recording")
        .put("source_type", "synthetic").put("count", 3).put("average_bpm", 72))
    private fun demoRows() = listOf(70, 72, 74).mapIndexed { index, bpm ->
        JSONObject().put("timestamp", "2026-11-22T09:0${index}:00Z").put("heart_rate", bpm)
    }
}
