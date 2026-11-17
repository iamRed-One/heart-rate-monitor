package com.example.myapplication.adapter

import com.example.myapplication.core.DeviceKind
import com.example.myapplication.core.Log
import com.example.myapplication.core.Metric
import com.example.myapplication.core.Payload
import com.yucheng.ycbtsdk.Constants
import com.yucheng.ycbtsdk.YCBTClient
import com.yucheng.ycbtsdk.response.BleConnectResponse
import com.yucheng.ycbtsdk.response.BleDataResponse
import com.yucheng.ycbtsdk.response.BleDeviceToAppDataResponse
import com.yucheng.ycbtsdk.response.BleRealDataResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * [DeviceAdapter] for the Q520 ring over the Yucheng `YCBTClient` SDK. Wraps the SDK's callbacks
 * into suspend calls and maps its BLEState codes into [LinkState]. Auto-reconnect is disabled here
 * (the SDK's reconnect produces a dead command channel) — reconnection is the ConnectionManager's job.
 *
 * Stream set mirrors the vendor app (HealthWear 1.1.93, `DataSyncUtils.syncData`): it pulls one
 * `healthHistoryData` type per supported feature — notably `Health_HistoryAll`, which is where the
 * ring keeps SpO2 / temperature / respiration / HRV / glucose — plus stored ECG/PPG recordings
 * (`EcgSyncUtils`: `collectEcgList` → `collectEcgDataWithTimestamp(collectSendTime)`).
 */
class RingAdapter : DeviceAdapter {

    override val kind = DeviceKind.RING

    private val _state = MutableStateFlow(LinkState.IDLE)
    override val state: StateFlow<LinkState> = _state.asStateFlow()

    @Volatile override var currentMac: String? = null
        private set

    private var listenerRegistered = false
    private val stateListener = BleConnectResponse { code -> mapState(code)?.let { _state.value = it } }

    // The SDK holds a single real-time callback; everything fans out from here.
    @Volatile private var liveSink: ((String, Map<String, Any?>) -> Unit)? = null
    @Volatile private var ecgCollector: ((HashMap<*, *>?) -> Unit)? = null
    @Volatile private var measureCollector: ((Int, HashMap<*, *>?) -> Unit)? = null
    private val realtime = BleRealDataResponse { dataType, map ->
        if (dataType == Constants.DATATYPE.Real_UploadECG) ecgCollector?.invoke(map)
        measureCollector?.invoke(dataType, map)
        liveSink?.invoke("ring.live.${realtimeName(dataType)}", withType(dataType, map))
    }
    private val deviceEvents = BleDeviceToAppDataResponse { dataType, map ->
        Log.d("ring", "device event type=$dataType $map")
        liveSink?.invoke("ring.device_event", withType(dataType, map))
    }

    override fun isReady(): Boolean = YCBTClient.connectState() == Constants.BLEState.ReadWriteOK

    private fun ensureListener() {
        if (listenerRegistered) return
        listenerRegistered = true
        runCatching { YCBTClient.setReconnect(false) }
        YCBTClient.registerBleStateChange(stateListener)
    }

    override suspend fun connect(mac: String): Boolean {
        ensureListener()
        currentMac = mac
        if (isReady()) { _state.value = LinkState.READY; Log.d("ring", "link already up, adopting it"); return true }
        _state.value = LinkState.CONNECTING
        Log.d("ring", "connect $mac")
        YCBTClient.connectBle(mac, BleConnectResponse { code -> Log.d("ring", "connect cb ${mapState(code)}") })
        val ok = withTimeoutOrNull(CONNECT_TIMEOUT_MS) {
            state.first { it == LinkState.READY || it == LinkState.ERROR || it == LinkState.TIMEOUT }
            isReady()
        } ?: false
        if (!ok && _state.value == LinkState.CONNECTING) _state.value = LinkState.TIMEOUT
        return ok
    }

    override fun disconnect() {
        _state.value = LinkState.DISCONNECTED
        runCatching { YCBTClient.disconnectBle() }
    }

    override suspend fun readDeviceInfo(): DeviceInfo? {
        if (!isReady()) return null
        val (_, infoMap) = awaitData { YCBTClient.getDeviceInfo(it) }
        val raw = infoMap?.entries?.associate { (k, v) -> k.toString() to v.toString() } ?: emptyMap()
        val flags = readSupportFlags()
        return DeviceInfo(
            deviceId = deviceId(),
            kind = DeviceKind.RING,
            model = runCatching { YCBTClient.getBindDeviceName() }.getOrNull() ?: "Q520",
            firmware = runCatching { YCBTClient.getBindDeviceVersion() }.getOrNull()
                ?: raw.entries.firstOrNull { it.key.contains("version", true) }?.value,
            battery = runCatching { YCBTClient.getDeviceBatteryValue() }.getOrNull()?.takeIf { it in 0..100 },
            featureFlags = flags,
            rawFeatures = Payload.toJson(infoMap),
        )
    }

    private suspend fun readSupportFlags(): Map<String, Boolean> {
        val (_, map) = awaitData { YCBTClient.getDeviceSupportFunction(it) }
        return map?.entries?.associate { (k, v) -> k.toString() to (v.toString().let { s -> s == "1" || s.equals("true", true) }) }
            ?: emptyMap()
    }

    override suspend fun syncClock(): Boolean {
        if (!isReady()) return false
        val (tz, _) = awaitData { YCBTClient.settingTimeZone(it) }
        val (t, _) = awaitData { YCBTClient.settingTime(it) }
        Log.d("ring", "clock sync tz=$tz time=$t")
        return t == 0
    }

    /**
     * Turn on continuous monitoring so the ring actually *records* SpO2/temperature/HRV history
     * (off by default; that's why those histories were empty). Called once per connection.
     */
    override suspend fun enableMonitoring() {
        if (!isReady()) return
        runCatching { awaitData { YCBTClient.settingHeartMonitor(1, MONITOR_INTERVAL_MIN, it) } }
        runCatching { awaitData { YCBTClient.settingTemperatureMonitor(true, MONITOR_INTERVAL_MIN, it) } }
        runCatching { awaitData { YCBTClient.settingBloodOxygenModeMonitor(true, MONITOR_INTERVAL_MIN, it) } }
        runCatching { awaitData { YCBTClient.settingHRVMonitor(1, MONITOR_INTERVAL_MIN, 0, 23, 59, it) } }
        runCatching { awaitData { YCBTClient.settingPpgCollect(1, 0, 0, it) } }
        Log.i("ring", "monitoring enabled (HR/temp/SpO2/HRV/PPG every $MONITOR_INTERVAL_MIN min)")
    }

    // --- Streams ---------------------------------------------------------------------------

    override fun streams(): List<String> = HISTORY.keys.toList() + listOf(STREAM_ECG_STORED, STREAM_PPG_STORED)

    override suspend fun pullStream(stream: String): RawStream? {
        if (!isReady()) return null
        val rows = when (stream) {
            STREAM_ECG_STORED -> pullRecordings(ecg = true)
            STREAM_PPG_STORED -> pullRecordings(ecg = false)
            else -> pullHistory(HISTORY[stream] ?: return null)
        }
        Log.d("ring", "stream $stream rows=${rows.size} first=${rows.firstOrNull()?.toString()?.take(300)}")
        return RawStream(stream, deviceId(), rows)
    }

    private suspend fun pullHistory(type: Int): List<Map<String, Any?>> {
        val (code, map) = awaitData(HISTORY_TIMEOUT_MS) { YCBTClient.healthHistoryData(type, it) }
        if (code != 0) Log.d("ring", "history type=$type code=$code")
        return rowsOf(map)
    }

    /** Stored ECG/PPG recordings: list the summaries, then download each one's raw bytes. */
    private suspend fun pullRecordings(ecg: Boolean): List<Map<String, Any?>> {
        val (_, listMap) = awaitData { if (ecg) YCBTClient.collectEcgList(it) else YCBTClient.collectPpgList(it) }
        val summaries = rowsOf(listMap)
        return summaries.map { summary ->
            val sendTime = (summary["collectSendTime"] as? Number)?.toLong()
            val (code, data) = if (sendTime == null) (-1 to null) else awaitData(RECORDING_TIMEOUT_MS) {
                if (ecg) YCBTClient.collectEcgDataWithTimestamp(sendTime, it) else YCBTClient.collectPpgDataWithTimestamp(sendTime, it)
            }
            mapOf("summary" to summary, "code" to code, "recording" to Payload.sanitize(data))
        }
    }

    private fun rowsOf(map: HashMap<*, *>?): List<Map<String, Any?>> {
        val dataList = mapVal(map, "data") as? List<*> ?: return emptyList()
        return dataList.mapNotNull { row ->
            @Suppress("UNCHECKED_CAST")
            (Payload.sanitize(row) as? Map<String, Any?>)
        }
    }

    // --- Live ------------------------------------------------------------------------------

    override fun startLive(sink: (stream: String, payload: Map<String, Any?>) -> Unit) {
        liveSink = sink
        runCatching { YCBTClient.appRegisterRealDataCallBack(realtime) }
        runCatching { YCBTClient.deviceToApp(deviceEvents) }
    }

    override fun stopLive() {
        liveSink = null
    }

    private fun withType(dataType: Int, map: HashMap<*, *>?): Map<String, Any?> {
        @Suppress("UNCHECKED_CAST")
        val body = (Payload.sanitize(map) as? Map<String, Any?>).orEmpty()
        return body + mapOf("_dataType" to dataType, "_rxMs" to System.currentTimeMillis())
    }

    private fun realtimeName(type: Int): String = REALTIME_NAMES[type] ?: "type_$type"

    // --- On-demand measurements ------------------------------------------------------------

    override fun measurableMetrics(): List<Metric> =
        listOf(Metric.HEART_RATE, Metric.SPO2, Metric.TEMPERATURE, Metric.BLOOD_PRESSURE)

    /**
     * Same flow as the vendor app (`BaseMeasureActivity.playStopMeasure`): tell the ring to start a
     * measurement of one type (`appStartMeasurement(1, type)`), read the values it streams back through
     * the real-time callback, then stop it. The old one-shot reads (`getAllRealDataFromDevice`,
     * `getRealTemp`, `getRealBloodOxygen`) answer `code=-4` on this firmware, so they are only a fallback.
     */
    override suspend fun measure(metric: Metric): List<com.example.myapplication.core.Reading> {
        if (!isReady()) return emptyList()
        val type = MEASURE_TYPE[metric] ?: return fallbackMeasure(metric)
        runCatching { YCBTClient.appRegisterRealDataCallBack(realtime) }

        val seen = java.util.Collections.synchronizedList(ArrayList<Pair<Int, Map<String, Any?>>>())
        measureCollector = { dataType, map ->
            @Suppress("UNCHECKED_CAST")
            val body = (Payload.sanitize(map) as? Map<String, Any?>).orEmpty()
            Log.d("ring", "measure live type=$dataType $body")
            seen.add(dataType to body)
        }
        val (startCode, _) = awaitData { YCBTClient.appStartMeasurement(1, type, it) }
        Log.d("ring", "measure $metric start type=$type code=$startCode")
        var found: List<com.example.myapplication.core.Reading> = emptyList()
        try {
            if (startCode == 0) {
                // A reading takes the ring ~20-40 s (it needs a steady signal); poll until one lands.
                val deadline = System.currentTimeMillis() + (MEASURE_WINDOW_MS[metric] ?: 60_000L)
                while (System.currentTimeMillis() < deadline && found.isEmpty()) {
                    kotlinx.coroutines.delay(500)
                    found = readingsFrom(metric, synchronized(seen) { seen.toList() })
                }
            }
        } finally {
            measureCollector = null
            runCatching { awaitData { YCBTClient.appStartMeasurement(0, type, it) } }
        }
        if (found.isNotEmpty()) return found
        Log.d("ring", "measure $metric got nothing from the live stream; trying the one-shot read")
        return fallbackMeasure(metric)
    }

    /** The last positive value the ring streamed for [metric], as canonical readings. */
    private fun readingsFrom(metric: Metric, frames: List<Pair<Int, Map<String, Any?>>>): List<com.example.myapplication.core.Reading> {
        val now = System.currentTimeMillis()
        fun num(m: Map<String, Any?>, vararg keys: String): Double? =
            keys.firstNotNullOfOrNull { k -> (m[k] as? Number)?.toDouble() ?: (m[k] as? String)?.toDoubleOrNull() }
        fun last(vararg keys: String): Double? = frames.asReversed().firstNotNullOfOrNull { (_, m) -> num(m, *keys)?.takeIf { it > 0 } }
        return when (metric) {
            Metric.HEART_RATE -> last("heartValue", "heart")?.let { listOf(com.example.myapplication.core.Reading.Scalar(metric, now, it, "bpm")) }
            Metric.SPO2 -> last("bloodOxygenValue", "bloodOxygen", "oxygen")?.let { listOf(com.example.myapplication.core.Reading.Scalar(metric, now, it, "%")) }
            Metric.TEMPERATURE -> {
                val whole = last("tempInteger", "tempIntValue")
                val temp = if (whole != null) whole + (last("tempFloat", "tempFloatValue") ?: 0.0).let { f -> if (f < 10) f / 10 else f / 100 }
                else last("tempValue", "temp")?.let { if (it > 60) it / 10.0 else it }
                temp?.let { listOf(com.example.myapplication.core.Reading.Scalar(metric, now, it, "\u00b0C")) }
            }
            Metric.BLOOD_PRESSURE -> frames.asReversed().firstNotNullOfOrNull { (_, m) ->
                val sbp = num(m, "bloodSBP")?.toInt(); val dbp = num(m, "bloodDBP")?.toInt()
                if (sbp != null && dbp != null && sbp > 0 && dbp > 0) sbp to dbp else null
            }?.let { (sbp, dbp) -> listOf(com.example.myapplication.core.Reading.BloodPressure(now, sbp, dbp, com.example.myapplication.core.Quality.ESTIMATED)) }
            else -> null
        }.orEmpty()
    }

    /** Old one-shot reads. Kept for firmware that answers them; on this ring they return `code=-4`. */
    private suspend fun fallbackMeasure(metric: Metric): List<com.example.myapplication.core.Reading> {
        val now0 = System.currentTimeMillis()
        when (metric) {
            Metric.TEMPERATURE -> {
                val (c, m) = awaitData { YCBTClient.getRealTemp(it) }
                Log.d("ring", "measure temp code=$c map=$m")
                dblOfMap(m, "tempValue", "temp", "value")?.takeIf { it > 0 }?.let { raw ->
                    val cC = if (raw > 60) raw / 10.0 else raw
                    return listOf(com.example.myapplication.core.Reading.Scalar(Metric.TEMPERATURE, now0, cC, "\u00b0C"))
                }
            }
            Metric.SPO2 -> {
                val (c, m) = awaitData { YCBTClient.getRealBloodOxygen(it) }
                Log.d("ring", "measure spo2 code=$c map=$m")
                dblOfMap(m, "bloodOxygenValue", "oxygen", "value")?.takeIf { it > 0 }?.let {
                    return listOf(com.example.myapplication.core.Reading.Scalar(Metric.SPO2, now0, it, "%"))
                }
            }
            else -> {}
        }
        val (code, map) = awaitData { YCBTClient.getAllRealDataFromDevice(it) }
        Log.d("ring", "measure code=$code map=$map")
        val now = System.currentTimeMillis()
        val out = ArrayList<com.example.myapplication.core.Reading>()
        intOf(map, "heartValue", "heart")?.takeIf { it > 0 }
            ?.let { out += com.example.myapplication.core.Reading.Scalar(Metric.HEART_RATE, now, it.toDouble(), "bpm") }
        intOf(map, "bloodOxygen", "spo2", "oxygen")?.takeIf { it > 0 }
            ?.let { out += com.example.myapplication.core.Reading.Scalar(Metric.SPO2, now, it.toDouble(), "%") }
        val tInt = intOf(map, "tempInteger"); val tFrac = intOf(map, "tempFloat")
        if (tInt != null && tInt > 0) {
            out += com.example.myapplication.core.Reading.Scalar(Metric.TEMPERATURE, now, tInt + (tFrac ?: 0) / 100.0, "\u00b0C")
        }
        val sbp = intOf(map, "bloodSBP"); val dbp = intOf(map, "bloodDBP")
        if (sbp != null && dbp != null && sbp > 0 && dbp > 0) {
            out += com.example.myapplication.core.Reading.BloodPressure(now, sbp, dbp, com.example.myapplication.core.Quality.ESTIMATED)
        }
        return out
    }

    private fun intOf(map: HashMap<*, *>?, vararg keys: String): Int? = keys.firstNotNullOfOrNull { k ->
        (mapVal(map, k) as? Number)?.toInt() ?: (mapVal(map, k) as? String)?.toIntOrNull()
    }

    private fun dblOfMap(map: HashMap<*, *>?, vararg keys: String): Double? = keys.firstNotNullOfOrNull { k ->
        (mapVal(map, k) as? Number)?.toDouble() ?: (mapVal(map, k) as? String)?.toDoubleOrNull()
    }

    // --- Raw signal recording (M5) ---------------------------------------------------------

    override fun recordableSignals(): List<Metric> = listOf(Metric.ECG)

    override suspend fun recordSignal(metric: Metric, durationMs: Long, onSamples: (List<Double>) -> Unit): com.example.myapplication.adapter.RawCapture? {
        if (!isReady() || metric != Metric.ECG) return null
        val batches = java.util.Collections.synchronizedList(ArrayList<com.example.myapplication.adapter.RawCapture.Batch>())
        val seq = java.util.concurrent.atomic.AtomicLong(0)
        ecgCollector = { map ->
            val values = waveformOf(map)
            if (values.isNotEmpty()) {
                batches.add(com.example.myapplication.adapter.RawCapture.Batch(System.currentTimeMillis(), seq.getAndIncrement(), values))
                onSamples(values)
            }
        }
        val cmd = BleDataResponse { code, _, _ -> Log.d("ring", "ecg cmd=$code") }
        runCatching { YCBTClient.appRegisterRealDataCallBack(realtime) }
        runCatching { YCBTClient.appEcgTestStart(cmd, realtime) }
        try {
            kotlinx.coroutines.delay(durationMs)
        } finally {
            runCatching { YCBTClient.appEcgTestEnd(cmd) }
            ecgCollector = null
        }

        val snapshot = batches.toList()
        val hz = if (snapshot.size > 1) {
            val span = (snapshot.last().tsMs - snapshot.first().tsMs).coerceAtLeast(1)
            (snapshot.sumOf { it.values.size.toLong() }) * 1000.0 / span
        } else 0.0
        Log.i("ring", "ecg recorded batches=${snapshot.size} samples=${snapshot.sumOf { it.values.size }} hz=${"%.1f".format(hz)}")
        return com.example.myapplication.adapter.RawCapture(Metric.ECG, deviceId(), snapshot, hz)
    }

    /** Pull the numeric waveform out of the vendor envelope's `data` array. */
    private fun waveformOf(map: HashMap<*, *>?): List<Double> {
        val payload = mapVal(map, "data") ?: mapVal(map, "originalData") ?: return emptyList()
        val out = ArrayList<Double>()
        fun collect(v: Any?) {
            when (v) {
                is Number -> out.add(v.toDouble())
                is Iterable<*> -> v.forEach { collect(it) }
                is IntArray -> v.forEach { out.add(it.toDouble()) }
            }
        }
        collect(payload)
        return out
    }

    private fun deviceId(): String =
        runCatching { YCBTClient.getBindDeviceMac() }.getOrNull()?.takeIf { it.isNotBlank() } ?: currentMac ?: "ring"

    private fun mapVal(map: HashMap<*, *>?, key: String): Any? =
        map?.entries?.firstOrNull { it.key?.toString() == key }?.value

    /** Wrap a one-shot command into a suspend call returning (code, map). */
    private suspend fun awaitData(timeoutMs: Long = CMD_TIMEOUT_MS, start: (BleDataResponse) -> Unit): Pair<Int, HashMap<*, *>?> =
        withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                start(BleDataResponse { code, _, map -> if (cont.isActive) cont.resume(code to map) })
            }
        } ?: (-99 to null)

    private fun mapState(code: Int): LinkState? = when (code) {
        Constants.BLEState.TimeOut -> LinkState.TIMEOUT
        Constants.BLEState.NotOpen -> LinkState.ERROR
        Constants.BLEState.Disconnect -> LinkState.DISCONNECTED
        Constants.BLEState.Disconnecting -> LinkState.DISCONNECTED
        Constants.BLEState.Connecting -> LinkState.CONNECTING
        Constants.BLEState.Connected,
        Constants.BLEState.ServicesDiscovered,
        Constants.BLEState.CharacteristicDiscovered,
        Constants.BLEState.CharacteristicNotification -> LinkState.CONNECTING
        Constants.BLEState.ReadWriteOK -> LinkState.READY
        else -> null   // ignore spurious code=0 / unknown so we don't flap out of READY
    }

    companion object {
        const val STREAM_ECG_STORED = "ring.ecg.stored"
        const val STREAM_PPG_STORED = "ring.ppg.stored"

        /** Stream name → `healthHistoryData` type. Order = the vendor app's order, then extras. */
        val HISTORY: Map<String, Int> = linkedMapOf(
            "ring.history.sport" to Constants.DATATYPE.Health_HistorySport,
            "ring.history.sleep" to Constants.DATATYPE.Health_HistorySleep,
            "ring.history.heart" to Constants.DATATYPE.Health_HistoryHeart,
            "ring.history.blood_pressure" to Constants.DATATYPE.Health_HistoryBlood,
            "ring.history.all" to Constants.DATATYPE.Health_HistoryAll,
            "ring.history.comprehensive" to Constants.DATATYPE.Health_HistoryComprehensiveMeasureData,
            "ring.history.sport_mode" to Constants.DATATYPE.Health_HistorySportMode,
            "ring.history.body" to Constants.DATATYPE.Health_History_Body_Data,
            "ring.history.spo2" to Constants.DATATYPE.Health_HistoryBloodOxygen,
            "ring.history.temperature" to Constants.DATATYPE.Health_HistoryTemp,
            "ring.history.temp_humidity" to Constants.DATATYPE.Health_HistoryTempAndHumidity,
            "ring.history.ambient_light" to Constants.DATATYPE.Health_HistoryAmbientLight,
            "ring.history.fall" to Constants.DATATYPE.Health_HistoryFall,
            "ring.history.health_monitoring" to Constants.DATATYPE.Health_HistoryHealthMonitoring,
            "ring.history.sedentary" to Constants.DATATYPE.Health_History_Sedentary_Data,
            "ring.history.warning" to Constants.DATATYPE.Health_HistoryWarning,
        )

        private val REALTIME_NAMES = mapOf(
            Constants.DATATYPE.Real_UploadSport to "sport",
            Constants.DATATYPE.Real_UploadHeart to "heart",
            Constants.DATATYPE.Real_UploadBloodOxygen to "spo2",
            Constants.DATATYPE.Real_UploadBlood to "blood",
            Constants.DATATYPE.Real_UploadPPG to "ppg",
            Constants.DATATYPE.Real_UploadECG to "ecg",
            Constants.DATATYPE.Real_UploadRun to "run",
            Constants.DATATYPE.Real_UploadRespiratoryRate to "respiration",
            Constants.DATATYPE.Real_UploadSensor to "sensor",
            Constants.DATATYPE.Real_UploadAmbientlight to "ambient_light",
            Constants.DATATYPE.Real_UploadComprehensive to "comprehensive",
            Constants.DATATYPE.Real_UploadBodyData to "body",
            Constants.DATATYPE.Real_UploadMulPhotoelectricWaveform to "multi_ppg",
            Constants.DATATYPE.Real_UploadECGHrv to "ecg_hrv",
            Constants.DATATYPE.Real_UploadECGRR to "ecg_rr",
            Constants.DATATYPE.Real_UploadEventReminder to "event_reminder",
        )

        private const val CONNECT_TIMEOUT_MS = 25_000L
        private const val CMD_TIMEOUT_MS = 8_000L
        private const val HISTORY_TIMEOUT_MS = 30_000L
        private const val RECORDING_TIMEOUT_MS = 60_000L
        private const val MONITOR_INTERVAL_MIN = 5
        /** How long to wait for the ring's first value: pulse lands in ~30 s, the optical ones take longer. */
        private val MEASURE_WINDOW_MS = mapOf(
            Metric.HEART_RATE to 45_000L,
            Metric.TEMPERATURE to 60_000L,
            Metric.SPO2 to 90_000L,
            Metric.BLOOD_PRESSURE to 90_000L,
        )

        /** `appStartMeasurement` type codes, from the vendor app's per-metric `getType()`. */
        private val MEASURE_TYPE = mapOf(
            Metric.HEART_RATE to 0,
            Metric.BLOOD_PRESSURE to 1,
            Metric.SPO2 to 2,
            Metric.TEMPERATURE to 4,
        )
    }
}
