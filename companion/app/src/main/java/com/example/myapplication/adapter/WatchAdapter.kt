package com.example.myapplication.adapter

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.example.myapplication.core.DeviceKind
import com.example.myapplication.core.Log
import com.example.myapplication.core.Metric
import com.example.myapplication.core.Payload
import com.inuker.bluetooth.library.Constants
import com.veepoo.protocol.VPOperateManager
import com.veepoo.protocol.listener.base.IBleWriteResponse
import com.veepoo.protocol.listener.base.IConnectResponse
import com.veepoo.protocol.listener.base.INotifyResponse
import com.veepoo.protocol.listener.data.IBatteryDataListener
import com.veepoo.protocol.listener.data.IDeviceFuctionDataListener
import com.veepoo.protocol.listener.data.IPersonInfoDataListener
import com.veepoo.protocol.listener.data.IPwdDataListener
import com.veepoo.protocol.listener.data.IResponseListener
import com.veepoo.protocol.listener.data.ISocialMsgDataListener
import com.veepoo.protocol.model.datas.BatteryData
import com.veepoo.protocol.model.datas.PwdData
import com.veepoo.protocol.model.enums.EPwdStatus
import com.veepoo.protocol.model.settings.DeviceTimeSetting
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Calendar
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * [DeviceAdapter] for the ET488 watch over the Veepoo `VPOperateManager` SDK. Connection sequence:
 * connectDevice → (notify/status) → confirmDevicePwd("0000") → syncPersonInfo → READY. Requires the
 * Nordic mcumgr deps (the SDK inits its OTA manager on connect, else NoClassDefFoundError).
 *
 * HBand itself talks a private protocol (`com.veepoo.hband.ble.readmanager.*`) rather than this SDK,
 * but every HBand reader has an SDK equivalent; [streams] covers the same set HBand syncs.
 */
class WatchAdapter(context: Context) : DeviceAdapter {

    private val vp = VPOperateManager.getMangerInstance(context.applicationContext)
    override val kind = DeviceKind.WATCH

    private val _state = MutableStateFlow(LinkState.IDLE)
    override val state: StateFlow<LinkState> = _state.asStateFlow()

    @Volatile override var currentMac: String? = null
        private set

    private val handler = Handler(Looper.getMainLooper())
    private val authStarted = AtomicBoolean(false)
    private val write = IBleWriteResponse { c -> Log.d("watch", "write rsp=$c") }
    // Some Veepoo APIs (ECG, body composition) take the inuker BleWriteResponse instead.
    private val inukerWrite = com.inuker.bluetooth.library.connect.response.BleWriteResponse { c -> Log.d("watch", "iwrite rsp=$c") }

    @Volatile private var fwVersion: String? = null
    @Volatile private var deviceNumber: Int? = null
    @Volatile private var featuresRaw: String? = null

    override fun isReady(): Boolean = _state.value == LinkState.READY

    override suspend fun connect(mac: String): Boolean {
        currentMac = mac
        authStarted.set(false)
        _state.value = LinkState.CONNECTING
        Log.d("watch", "connect $mac")

        // The Veepoo/inuker SDK builds Handler-backed listeners, so all SDK setup must run on the
        // main (Looper) thread — constructing them off-thread throws IllegalStateException.
        withContext(Dispatchers.Main) {
            vp.registerConnectStatusListener(mac, object : com.veepoo.protocol.listener.base.IABleConnectStatusListener() {
                override fun onConnectStatusChanged(m: String?, status: Int) {
                    Log.d("watch", "status -> $status")
                    when (status) {
                        Constants.STATUS_DISCONNECTED -> { authStarted.set(false); _state.value = LinkState.DISCONNECTED }
                        Constants.STATUS_CONNECTED -> handler.postDelayed({ startAuth("status") }, 1500)
                    }
                }
            })
            if (vp.isCurrentDeviceConnected()) handler.postDelayed({ startAuth("already") }, 500)
            vp.connectDevice(
                mac,
                IConnectResponse { _, _, success -> if (success) _state.value = LinkState.CONNECTING },
                INotifyResponse { startAuth("notify") },
            )
        }

        val ok = withTimeoutOrNull(CONNECT_TIMEOUT_MS) {
            state.first { it == LinkState.READY || it == LinkState.ERROR || it == LinkState.TIMEOUT }
            _state.value == LinkState.READY
        } ?: false
        if (!ok && _state.value != LinkState.READY) _state.value = LinkState.TIMEOUT
        return ok
    }

    private fun startAuth(reason: String) {
        if (!authStarted.compareAndSet(false, true)) return
        _state.value = LinkState.AUTHENTICATING
        Log.d("watch", "auth ($reason)")
        vp.confirmDevicePwd(
            write,
            object : IPwdDataListener {
                override fun onPwdDataChange(d: PwdData?) {
                    fwVersion = d?.deviceVersion
                    deviceNumber = d?.deviceNumber
                    val ok = d?.getmStatus() == EPwdStatus.CHECK_SUCCESS || d?.getmStatus() == EPwdStatus.CHECK_AND_TIME_SUCCESS
                    Log.d("watch", "pwd=${d?.getmStatus()}")
                    if (ok) { syncPersonInfo(); _state.value = LinkState.READY } else _state.value = LinkState.ERROR
                }
                override fun onConnectionConfirmTimeout() { _state.value = LinkState.TIMEOUT }
            },
            object : IDeviceFuctionDataListener {
                override fun onFunctionSupportDataChange(d: com.veepoo.protocol.model.datas.FunctionDeviceSupportData?) { featuresRaw = d?.toString() }
                override fun onDeviceFunctionPackage1Report(d: com.veepoo.protocol.model.datas.DeviceFunctionPackage1?) {}
                override fun onDeviceFunctionPackage2Report(d: com.veepoo.protocol.model.datas.DeviceFunctionPackage2?) {}
                override fun onDeviceFunctionPackage3Report(d: com.veepoo.protocol.model.datas.DeviceFunctionPackage3?) {}
                override fun onDeviceFunctionPackage4Report(d: com.veepoo.protocol.model.datas.DeviceFunctionPackage4?) {}
                override fun onDeviceFunctionPackage5Report(d: com.veepoo.protocol.model.datas.DeviceFunctionPackage5?) {}
            },
            object : ISocialMsgDataListener {
                override fun onSocialMsgSupportDataChange(d: com.veepoo.protocol.model.datas.FunctionSocailMsgData?) {}
                override fun onSocialMsgSupportDataChange2(d: com.veepoo.protocol.model.datas.FunctionSocailMsgData?) {}
            },
            PASSWORD, false,
        )
    }

    private fun syncPersonInfo() {
        runCatching {
            vp.syncPersonInfo(
                write,
                IPersonInfoDataListener { s -> Log.d("watch", "personInfo=$s") },
                com.veepoo.protocol.model.datas.PersonInfoData(com.veepoo.protocol.model.enums.ESex.MAN, 175, 70, 30, 0),
            )
        }
    }

    override fun disconnect() {
        _state.value = LinkState.DISCONNECTED
        authStarted.set(false)
        handler.removeCallbacksAndMessages(null)
        runCatching { vp.disconnectWatch(write) }
    }

    override suspend fun readDeviceInfo(): DeviceInfo? {
        if (!isReady()) return null
        val battery = awaitBattery()
        return DeviceInfo(
            deviceId = currentMac ?: "watch",
            kind = DeviceKind.WATCH,
            model = deviceNumber?.let { "ET488#$it" } ?: "ET488",
            firmware = fwVersion,
            battery = battery,
            featureFlags = emptyMap(),         // Veepoo flags are opaque; kept raw below
            rawFeatures = featuresRaw,
        )
    }

    // --- Streams (mirrors HBand 11.0.17 `BleReadManager.readData` chain) --------------------

    override fun streams(): List<String> = listOf(
        STREAM_ORIGIN, STREAM_SLEEP, STREAM_SPO2, STREAM_HRV, STREAM_TEMPERATURE,
        STREAM_SPORT_MODEL, STREAM_BODY_COMPOSITION, STREAM_ECG_STORED, STREAM_RR_INTERVAL,
    )

    override suspend fun pullStream(stream: String): RawStream? {
        if (!isReady()) return null
        val rows = when (stream) {
            STREAM_ORIGIN -> readOrigin()
            STREAM_SLEEP -> readSleep()
            STREAM_SPO2 -> readSpo2Origin()
            STREAM_HRV -> readHrvOrigin()
            STREAM_TEMPERATURE -> readTemperature()
            STREAM_SPORT_MODEL -> readSportModel()
            STREAM_BODY_COMPOSITION -> readBodyComposition()
            STREAM_ECG_STORED -> readStoredEcg()
            STREAM_RR_INTERVAL -> readRrIntervals()
            else -> return null
        }
        Log.d("watch", "stream $stream rows=${rows.size} first=${rows.firstOrNull()?.toString()?.take(300)}")
        rows.groupBy { it["_kind"] }.forEach { (k, rs) -> if (k != null) Log.d("watch", "  $stream[$k] n=${rs.size} sample=${rs.last().toString().take(900)}") }
        return RawStream(stream, currentMac ?: "watch", rows)
    }

    /** Full dump of a Veepoo model object, stamped with the record's own time as `tsMs`. */
    private fun row(obj: Any?, time: com.veepoo.protocol.model.datas.TimeData?, kind: String? = null): Map<String, Any?> {
        val base = Payload.ofObject(obj) + ("tsMs" to tsOf(time))
        return if (kind == null) base else base + ("_kind" to kind)
    }

    /**
     * 5-minute + half-hour "origin" data. Protocol v3/5 firmwares batch it as OriginData3 (with
     * PPG/ECG/respiration/apnea/glucose arrays) via IOriginData3Listener; older ones deliver per item.
     */
    private suspend fun readOrigin(): List<Map<String, Any?>> = gather(ORIGIN_TIMEOUT_MS) { emit, done ->
        val listener = object :
            com.veepoo.protocol.listener.data.IOriginDataListener,
            com.veepoo.protocol.listener.data.IOriginData3Listener {
            override fun onOringinFiveMinuteDataChange(d: com.veepoo.protocol.model.datas.OriginData?) { d?.let { emit(row(it, it.getmTime(), "5min")) } }
            override fun onOringinHalfHourDataChange(d: com.veepoo.protocol.model.datas.OriginHalfHourData?) { d?.let { emit(row(it, null, "30min")) } }
            override fun onOriginFiveMinuteListDataChange(list: MutableList<com.veepoo.protocol.model.datas.OriginData3>?) { list?.forEach { emit(row(it, it.getmTime(), "5min")) } }
            override fun onOriginHalfHourDataChange(d: com.veepoo.protocol.model.datas.OriginHalfHourData?) { d?.let { emit(row(it, null, "30min")) } }
            override fun onOriginHRVOriginListDataChange(list: MutableList<com.veepoo.protocol.model.datas.HRVOriginData>?) { list?.forEach { emit(row(it, it.getmTime(), "hrv")) } }
            override fun onOriginSpo2OriginListDataChange(list: MutableList<com.veepoo.protocol.model.datas.Spo2hOriginData>?) { list?.forEach { emit(row(it, it.getmTime(), "spo2")) } }
            override fun onReadOriginProgressDetail(a: Int, b: String?, c: Int, e: Int) {}
            override fun onReadOriginProgress(p: Float) {}
            override fun onReadOriginComplete() = done()
            override fun onReadTimeout(i: Int) = done()
        }
        vp.readOriginData(write, listener, WATCH_DAYS)
    }

    private suspend fun readSleep(): List<Map<String, Any?>> = gather { emit, done ->
        vp.readSleepData(write, object : com.veepoo.protocol.listener.data.ISleepDataListener {
            override fun onSleepDataChange(day: String?, d: com.veepoo.protocol.model.datas.SleepData?) {
                d ?: return
                emit(row(d, d.sleepDown) + mapOf("endMs" to tsOf(d.sleepUp), "_day" to day))
            }
            override fun onSleepProgress(p: Float) {}
            override fun onSleepProgressDetail(day: String?, i: Int) {}
            override fun onReadSleepComplete() = done()
        }, WATCH_DAYS)
    }

    private suspend fun readSpo2Origin(): List<Map<String, Any?>> = gather(ORIGIN_TIMEOUT_MS) { emit, done ->
        vp.readSpo2hOrigin(write, object : com.veepoo.protocol.listener.data.ISpo2hOriginDataListener {
            override fun onReadOriginProgress(p: Float) {}
            override fun onReadOriginProgressDetail(a: Int, b: String?, c: Int, e: Int) {}
            override fun onSpo2hOriginListener(d: com.veepoo.protocol.model.datas.Spo2hOriginData?) { d?.let { emit(row(it, it.getmTime())) } }
            override fun onReadOriginComplete() = done()
        }, WATCH_DAYS)
    }

    private suspend fun readHrvOrigin(): List<Map<String, Any?>> = gather(ORIGIN_TIMEOUT_MS) { emit, done ->
        vp.readHRVOrigin(write, object : com.veepoo.protocol.listener.data.IHRVOriginDataListener {
            override fun onReadOriginProgress(p: Float) {}
            override fun onReadOriginProgressDetail(a: Int, b: String?, c: Int, e: Int) {}
            override fun onHRVOriginListener(d: com.veepoo.protocol.model.datas.HRVOriginData?) { d?.let { emit(row(it, it.getmTime())) } }
            override fun onDayHrvScore(day: Int, date: String?, score: Int) { emit(mapOf("_kind" to "dayScore", "day" to day, "date" to date, "score" to score)) }
            override fun onReadOriginComplete() = done()
        }, WATCH_DAYS)
    }

    private suspend fun readTemperature(): List<Map<String, Any?>> = gather { emit, done ->
        vp.readTemptureDataBySetting(write, object : com.veepoo.protocol.listener.data.ITemptureDataListener {
            override fun onTemptureDataListDataChange(list: MutableList<com.veepoo.protocol.model.datas.TemptureData>?) { list?.forEach { emit(row(it, it.getmTime())) } }
            override fun onReadOriginProgressDetail(a: Int, b: String?, c: Int, e: Int) {}
            override fun onReadOriginProgress(p: Float) {}
            override fun onReadOriginComplete() = done()
            override fun onReadTimeout(i: Int) = done()
        }, com.veepoo.protocol.model.settings.ReadOriginSetting(0, 1, false, WATCH_DAYS))
    }

    private suspend fun readSportModel(): List<Map<String, Any?>> = gather { emit, done ->
        vp.readSportModelOrigin(write, object : com.veepoo.protocol.listener.data.ISportModelOriginListener {
            override fun onReadOriginProgress(p: Float) {}
            override fun onReadOriginProgressDetail(a: Int, b: String?, c: Int, e: Int) {}
            override fun onHeadChangeListListener(h: com.veepoo.protocol.model.datas.SportModelOriginHeadData?) { h?.let { emit(Payload.ofObject(it) + ("_kind" to "head")) } }
            override fun onGPSWatchSportModeHeadChange(h: com.veepoo.protocol.model.datas.SportModelGPSWatchOriginHeadData?) { h?.let { emit(Payload.ofObject(it) + ("_kind" to "gpsHead")) } }
            override fun onItemChangeListListener(items: MutableList<com.veepoo.protocol.model.datas.SportModelOriginItemData>?) { items?.forEach { emit(Payload.ofObject(it) + ("_kind" to "item")) } }
            override fun onReadOriginComplete() = done()
        })
    }

    private suspend fun readBodyComposition(): List<Map<String, Any?>> = gather { emit, done ->
        vp.readBodyComponentData(inukerWrite, object : com.veepoo.protocol.listener.data.IBodyComponentReadDataListener {
            override fun readBodyComponentDataFinish(list: List<com.veepoo.protocol.model.datas.BodyComponent>?) {
                list?.forEach { emit(Payload.ofObject(it)) }
                done()
            }
        })
    }

    /** ECG recordings stored on the watch (manual + automatic), with the watch's own diagnosis. */
    private suspend fun readStoredEcg(): List<Map<String, Any?>> = gather(ECG_TIMEOUT_MS) { emit, done ->
        val since = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -ECG_LOOKBACK_DAYS) }
        val from = com.veepoo.protocol.model.datas.TimeData(
            since.get(Calendar.YEAR), since.get(Calendar.MONTH) + 1, since.get(Calendar.DAY_OF_MONTH), 0, 0, 0,
        )
        vp.readECGData(inukerWrite, from, com.veepoo.protocol.model.enums.EEcgDataType.ALL, object : com.veepoo.protocol.listener.data.IECGReadDataListener {
            override fun readDataFinish(list: MutableList<com.veepoo.protocol.model.datas.EcgDetectResult>?) {
                list?.forEach { emit(Payload.ofObject(it) + ("_kind" to "result")) }
                done()
            }
            override fun readDiagnosisDataFinish(list: MutableList<com.veepoo.protocol.model.datas.EcgDiagnosis>?) {
                list?.forEach { emit(Payload.ofObject(it) + ("_kind" to "diagnosis")) }
            }
        })
    }

    /** Beat-to-beat RR intervals for today, yesterday and the day before. */
    private suspend fun readRrIntervals(): List<Map<String, Any?>> {
        val out = ArrayList<Map<String, Any?>>()
        for (day in com.veepoo.protocol.model.DayState.values()) {
            out += gather { emit, done ->
                vp.readRRIntervalByDay(write, object : com.veepoo.protocol.listener.data.IRRIntervalProgressListener {
                    override fun onReadRRIntervalProgressChanged(p: Float, d: com.veepoo.protocol.model.datas.RRIntervalData?) {}
                    override fun onReadRRIntervalComplete(state: com.veepoo.protocol.model.DayState?, list: MutableList<com.veepoo.protocol.model.datas.RRIntervalData>?) {
                        list?.forEach { emit(Payload.ofObject(it) + ("_day" to day.name)) }
                        done()
                    }
                }, day, WATCH_DAYS)
            }
        }
        return out
    }

    /**
     * Run one Veepoo read on the main thread (its listeners are Handler-backed), collecting every
     * emitted row until the SDK signals completion or [timeoutMs] passes (keeps partial results).
     */
    private suspend fun gather(
        timeoutMs: Long = HISTORY_TIMEOUT_MS,
        start: (emit: (Map<String, Any?>) -> Unit, done: () -> Unit) -> Unit,
    ): List<Map<String, Any?>> {
        val out = java.util.Collections.synchronizedList(ArrayList<Map<String, Any?>>())
        withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<Unit> { cont ->
                val done = { if (cont.isActive) cont.resume(Unit) }
                handler.post {
                    runCatching { start({ out.add(it) }, done) }
                        .onFailure { Log.w("watch", "read failed", it); done() }
                }
            }
        }
        return out.toList()
    }

    // --- On-demand measurements (M4) -------------------------------------------------------

    override fun measurableMetrics(): List<Metric> =
        listOf(Metric.HEART_RATE, Metric.SPO2, Metric.TEMPERATURE, Metric.RESPIRATORY_RATE, Metric.BODY_COMPOSITION)

    /** Keep the detector open for one collection cycle; timestamp each new callback. */
    suspend fun captureHeartRate(durationMs: Long, onSample: (com.example.myapplication.core.Reading.Scalar) -> Unit): Boolean {
        if (!isReady()) return false
        val listener = com.veepoo.protocol.listener.data.IHeartDataListener { data ->
            data?.data?.takeIf { it in 1..300 }?.let { bpm ->
                onSample(com.example.myapplication.core.Reading.Scalar(
                    Metric.HEART_RATE, System.currentTimeMillis(), bpm.toDouble(), "bpm"))
            }
        }
        try {
            withContext(kotlinx.coroutines.Dispatchers.Main) { vp.startDetectHeart(write, listener) }
            kotlinx.coroutines.delay(durationMs.coerceIn(1_000L, 120_000L))
        } finally {
            withContext(kotlinx.coroutines.NonCancellable + kotlinx.coroutines.Dispatchers.Main) {
                stopHeartDetection()
            }
        }
        return true
    }


    override suspend fun measure(metric: Metric): List<com.example.myapplication.core.Reading> {
        if (!isReady()) return emptyList()
        val now = System.currentTimeMillis()
        val reading = when (metric) {
            Metric.RESPIRATORY_RATE -> awaitMeasure({ done ->
                vp.startDetectBreath(write, com.veepoo.protocol.listener.data.IBreathDataListener { d ->
                    (d?.value?.takeIf { it > 0 })?.let { done(com.example.myapplication.core.Reading.Scalar(Metric.RESPIRATORY_RATE, now, it.toDouble(), "brpm")) }
                })
            }, { runCatching { vp.stopDetectBreath(write, com.veepoo.protocol.listener.data.IBreathDataListener { }) } })

            Metric.BODY_COMPOSITION -> awaitMeasure({ done ->
                vp.startDetectBodyComponent(inukerWrite, object : com.veepoo.protocol.listener.data.IBodyComponentDetectListener {
                    override fun onDetecting(a: Int, b: Int) {}
                    override fun onDetectSuccess(d: com.veepoo.protocol.model.datas.BodyComponent) {
                        d.bodyFatRate.takeIf { it > 0f }?.let { done(com.example.myapplication.core.Reading.BodyComposition(now, it.toDouble())) }
                    }
                    override fun onDetectFailed(s: com.veepoo.protocol.model.enums.DetectState) {}
                    override fun onDetectStop() {}
                })
            }, { runCatching { vp.stopDetectBodyComponent(inukerWrite) } })

            Metric.HEART_RATE -> awaitMeasure({ done ->
                vp.startDetectHeart(write, com.veepoo.protocol.listener.data.IHeartDataListener { d ->
                    (d?.data?.takeIf { it > 0 })?.let { done(com.example.myapplication.core.Reading.Scalar(Metric.HEART_RATE, System.currentTimeMillis(), it.toDouble(), "bpm")) }
                })
            }, { stopHeartDetection() })

            Metric.SPO2 -> awaitMeasure({ done ->
                vp.startDetectSPO2H(write, com.veepoo.protocol.listener.data.ISpo2hDataListener { d ->
                    (d?.value?.takeIf { it > 0 })?.let { done(com.example.myapplication.core.Reading.Scalar(Metric.SPO2, now, it.toDouble(), "%")) }
                })
            }, { runCatching { vp.stopDetectSPO2H(write, com.veepoo.protocol.listener.data.ISpo2hDataListener { }) } })

            Metric.TEMPERATURE -> awaitMeasure({ done ->
                vp.startDetectTempture(write, com.veepoo.protocol.listener.data.ITemptureDetectDataListener { d ->
                    (d?.tempture?.takeIf { it > 0f })?.let { done(com.example.myapplication.core.Reading.Scalar(Metric.TEMPERATURE, now, it.toDouble(), "°C")) }
                })
            }, { runCatching { vp.stopDetectTempture(write, com.veepoo.protocol.listener.data.ITemptureDetectDataListener { }) } })

            else -> null
        }
        return reading?.let { listOf(it) } ?: emptyList()
    }

    override fun recordableSignals(): List<Metric> = listOf(Metric.ECG)

    override suspend fun recordSignal(metric: Metric, durationMs: Long, onSamples: (List<Double>) -> Unit): com.example.myapplication.adapter.RawCapture? {
        if (!isReady() || metric != Metric.ECG) return null
        val batches = java.util.Collections.synchronizedList(ArrayList<com.example.myapplication.adapter.RawCapture.Batch>())
        val seq = java.util.concurrent.atomic.AtomicLong(0)
        val listener = object : com.veepoo.protocol.listener.data.IECGDetectListener {
            override fun onEcgADCChange(raw: IntArray?, filtered: IntArray?) {
                val arr = filtered ?: raw ?: return
                if (arr.isEmpty()) return
                val values = arr.map { it.toDouble() }
                batches.add(com.example.myapplication.adapter.RawCapture.Batch(System.currentTimeMillis(), seq.getAndIncrement(), values))
                onSamples(values)
            }
            override fun onEcgDetectInfoChange(d: com.veepoo.protocol.model.datas.EcgDetectInfo?) {}
            override fun onEcgDetectStateChange(d: com.veepoo.protocol.model.datas.EcgDetectState?) {}
            override fun onEcgDetectResultChange(d: com.veepoo.protocol.model.datas.EcgDetectResult?) {}
            override fun onEcgDetectDiagnosisChange(d: com.veepoo.protocol.model.datas.EcgDiagnosis?) {}
        }
        handler.post { runCatching { vp.startDetectECG(inukerWrite, true, listener) } }
        try {
            kotlinx.coroutines.delay(durationMs)
        } finally {
            handler.post { runCatching { vp.stopDetectECG(inukerWrite, true, listener) } }
        }
        val snap = batches.toList()
        val hz = if (snap.size > 1) {
            val span = (snap.last().tsMs - snap.first().tsMs).coerceAtLeast(1)
            snap.sumOf { it.values.size.toLong() } * 1000.0 / span
        } else 0.0
        Log.i("watch", "ecg recorded batches=${snap.size} samples=${snap.sumOf { it.values.size }} hz=${"%.1f".format(hz)}")
        return com.example.myapplication.adapter.RawCapture(Metric.ECG, currentMac ?: "watch", snap, hz)
    }

    /** Start a measurement, resume on the first valid reading (or timeout), then stop. */
    private suspend fun awaitMeasure(
        start: (done: (com.example.myapplication.core.Reading) -> Unit) -> Unit,
        stop: suspend () -> Unit,
    ): com.example.myapplication.core.Reading? = try {
        withTimeoutOrNull(MEASURE_TIMEOUT_MS) {
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { cont ->
                    start { reading -> if (cont.isActive) cont.resume(reading) }
                }
            }
        }
    } finally {
        withContext(kotlinx.coroutines.NonCancellable + Dispatchers.Main) { stop() }
    }

    private suspend fun stopHeartDetection() {
        withTimeoutOrNull(CMD_TIMEOUT_MS) {
            suspendCancellableCoroutine<Unit> { cont ->
                runCatching {
                    vp.stopDetectHeart(IBleWriteResponse { code ->
                        Log.d("watch", "heart detector stop response=$code")
                        if (cont.isActive) cont.resume(Unit)
                    })
                }.onFailure { if (cont.isActive) cont.resume(Unit) }
            }
        }
    }

    /** TimeData (Y/M/D H:M:S) → epoch ms. */
    private fun tsOf(t: com.veepoo.protocol.model.datas.TimeData?): Long {
        if (t == null) return 0L
        val c = Calendar.getInstance()
        c.set(t.year, (t.month - 1).coerceAtLeast(0), t.day, t.hour, t.minute, t.second)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    private suspend fun awaitBattery(): Int? = withTimeoutOrNull(CMD_TIMEOUT_MS) {
        suspendCancellableCoroutine<Int?> { cont ->
            vp.readBattery(write, IBatteryDataListener { d: BatteryData? -> if (cont.isActive) cont.resume(d?.batteryPercent) })
        }
    }

    override suspend fun syncClock(): Boolean {
        if (!isReady()) return false
        val c = Calendar.getInstance()
        val setting = DeviceTimeSetting(
            c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH),
            c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), c.get(Calendar.SECOND),
            com.veepoo.protocol.model.enums.ETimeMode.MODE_24,   // else settingTime NPEs on a null mode
        )
        val code = withTimeoutOrNull(CMD_TIMEOUT_MS) {
            suspendCancellableCoroutine<Int> { cont ->
                vp.settingTime(write, IResponseListener { r -> if (cont.isActive) cont.resume(r) }, setting)
            }
        }
        Log.d("watch", "clock sync code=$code")
        return code != null
    }

    companion object {
        const val STREAM_ORIGIN = "watch.origin"
        const val STREAM_SLEEP = "watch.sleep"
        const val STREAM_SPO2 = "watch.spo2_origin"
        const val STREAM_HRV = "watch.hrv_origin"
        const val STREAM_TEMPERATURE = "watch.temperature"
        const val STREAM_SPORT_MODEL = "watch.sport_model"
        const val STREAM_BODY_COMPOSITION = "watch.body_composition"
        const val STREAM_ECG_STORED = "watch.ecg.stored"
        const val STREAM_RR_INTERVAL = "watch.rr_interval"

        private const val PASSWORD = "0000"
        private const val WATCH_DAYS = 3            // the watch keeps today + 2 previous days
        private const val ECG_LOOKBACK_DAYS = 30
        private const val CONNECT_TIMEOUT_MS = 30_000L
        private const val CMD_TIMEOUT_MS = 8_000L
        private const val HISTORY_TIMEOUT_MS = 30_000L
        private const val ORIGIN_TIMEOUT_MS = 90_000L
        private const val ECG_TIMEOUT_MS = 60_000L
        private const val MEASURE_TIMEOUT_MS = 45_000L
    }
}
