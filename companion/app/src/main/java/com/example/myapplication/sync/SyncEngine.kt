package com.example.myapplication.sync

import android.content.Context
import com.example.myapplication.adapter.LinkState
import com.example.myapplication.adapter.RingAdapter
import com.example.myapplication.adapter.StandardHeartAdapter
import com.example.myapplication.adapter.WatchAdapter
import com.example.myapplication.core.DeviceKind
import com.example.myapplication.heart.HeartCollector
import com.example.myapplication.heart.HeartMonitor
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject

data class HeartDevice(val deviceId: String, val kind: DeviceKind, val model: String, val battery: Int? = null)

/** Connects supported devices without unrelated history pulls or clinic jobs. */
class SyncEngine(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val preferences = context.getSharedPreferences("heart-devices", Context.MODE_PRIVATE)
    private val scanner = Scanner(context)
    private val watch = ConnectionManager(WatchAdapter(context), scope)
    private val ring = ConnectionManager(RingAdapter(), scope)
    private val standard = ConnectionManager(StandardHeartAdapter(context), scope)
    private val mutableDevices = MutableStateFlow<List<HeartDevice>>(restore())
    val devices = mutableDevices.asStateFlow()
    private val mutableScans = MutableStateFlow<List<ScanResult>>(emptyList())
    val scanResults = mutableScans.asStateFlow()
    private val mutableScanning = MutableStateFlow(false)
    val scanning = mutableScanning.asStateFlow()
    val watchState get() = watch.state
    val ringState get() = ring.state
    val standardState get() = standard.state
    val monitor = HeartMonitor(scope, ::connection)
    val collector = HeartCollector(context, scope, ::connection, monitor)
    private var scanJob: Job? = null

    init {
        scope.launch {
            combine(monitor.state, collector.state) { live, saving -> live.active || saving.active }
                .distinctUntilChanged().collect { active ->
                    if (!active && SyncService.running.value) SyncService.stop(appContext)
                }
        }
        listOf(watch, ring, standard).forEach { manager ->
            scope.launch {
                manager.state.collect { state ->
                    if (state == LinkState.READY) {
                        runCatching {
                            manager.execute("identity") { it.readDeviceInfo() }?.let { info ->
                                remember(HeartDevice(info.deviceId, info.kind, info.model, info.battery))
                            }
                        }
                        monitor.connected(manager.kind)
                    } else if (monitor.state.value.kind == manager.kind) {
                        monitor.disconnected()
                    }
                }
            }
        }
    }

    fun readHeartRate(kind: DeviceKind) {
        scope.launch(Dispatchers.Main) {
            runCatching { SyncService.start(appContext); monitor.start(kind) }
                .onFailure { monitor.stop("Unable to start reading. Reconnect your watch.") }
        }
    }

    fun startRecording(kind: DeviceKind, pairingCode: String) {
        scope.launch(Dispatchers.Main) {
            runCatching { SyncService.start(appContext); collector.start(kind, pairingCode) }
                .onFailure { monitor.stop("Unable to start recording. Try again.") }
        }
    }

    fun stopReading() { collector.stop(); monitor.stop() }

    private fun connection(kind: DeviceKind) = when (kind) {
        DeviceKind.WATCH -> watch
        DeviceKind.RING -> ring
        DeviceKind.UNKNOWN -> standard
    }

    fun startScan() {
        stopScan()
        mutableScans.value = emptyList()
        mutableScanning.value = true
        try {
            scanner.start { result ->
                mutableScans.update { current -> (current.filterNot { it.mac == result.mac } + result).sortedByDescending { it.rssi } }
            }
            scanJob = scope.launch { delay(8500); stopScan() }
        } catch (error: Exception) {
            mutableScanning.value = false
            throw error
        }
    }
    fun stopScan() { scanner.stop(); scanJob?.cancel(); mutableScanning.value = false }
    fun connect(result: ScanResult) {
        stopScan()
        remember(HeartDevice(result.mac, result.kind, result.name))
        connection(result.kind).requestConnect(result.mac)
    }
    fun reconnect(kind: DeviceKind) {
        devices.value.firstOrNull { it.kind == kind }?.let { connection(kind).requestConnect(it.deviceId) }
        if (connection(kind).isReady) monitor.connected(kind)
    }
    fun disconnect(kind: DeviceKind) {
        scope.launch {
            if (monitor.state.value.kind == kind) {
                collector.stop()
                monitor.stop()
                monitor.awaitStopped()
                monitor.disconnected()
            }
            withContext(Dispatchers.Main) { connection(kind).requestDisconnect() }
        }
    }
    private fun remember(device: HeartDevice) {
        mutableDevices.update { current -> current.filterNot { it.kind == device.kind } + device }
        val rows = JSONArray()
        devices.value.forEach { rows.put(JSONObject().put("id", it.deviceId).put("kind", it.kind.name).put("model", it.model)) }
        preferences.edit().putString("devices", rows.toString()).apply()
    }
    private fun restore(): List<HeartDevice> = runCatching {
        val rows = JSONArray(preferences.getString("devices", "[]"))
        List(rows.length()) { index -> rows.getJSONObject(index).let { HeartDevice(it.getString("id"), DeviceKind.valueOf(it.getString("kind")), it.getString("model")) } }
    }.getOrDefault(emptyList())
}
