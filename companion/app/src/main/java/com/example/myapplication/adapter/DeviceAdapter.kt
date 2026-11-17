package com.example.myapplication.adapter

import com.example.myapplication.core.DeviceKind
import com.example.myapplication.core.Metric
import com.example.myapplication.core.Reading
import kotlinx.coroutines.flow.StateFlow

/** High-level link state, decoupled from each SDK's raw connection codes. */
enum class LinkState { IDLE, SCANNING, CONNECTING, AUTHENTICATING, READY, DISCONNECTED, TIMEOUT, ERROR }

/** Device identity + capabilities read once after connecting. */
data class DeviceInfo(
    val deviceId: String,
    val kind: DeviceKind,
    val model: String,
    val firmware: String?,
    val battery: Int?,
    /** Vendor feature flags, normalised to our names. */
    val featureFlags: Map<String, Boolean>,
    /** Unparsed vendor capability blob, kept verbatim for later analysis (watch flags are opaque). */
    val rawFeatures: String? = null,
)

/**
 * The clean boundary over a vendor SDK. Implementations (ring = Yucheng, watch = Veepoo) are the
 * ONLY code that touches vendor types; everything above consumes this interface. All device
 * interaction is `suspend` so the sync engine can serialise it (the SDKs are single-operation).
 *
 * Capture model: a device exposes named **streams** (every history/storage area the vendor app
 * reads, found by reverse-engineering HealthWear / HBand). Each pull returns the vendor rows
 * verbatim; the engine stores every row untouched and only then derives normalised readings.
 */
interface DeviceAdapter {
    val kind: DeviceKind
    val state: StateFlow<LinkState>
    val currentMac: String?

    fun isReady(): Boolean

    /** Connect and (for the watch) authenticate. Suspends until READY or failure; returns READY. */
    suspend fun connect(mac: String): Boolean

    /** Explicit user disconnect (suppresses auto-reconnect upstream). */
    fun disconnect()

    /** Read device info + feature flags. Requires READY. */
    suspend fun readDeviceInfo(): DeviceInfo?

    /** Push the phone clock to the device so timestamps align. Requires READY. */
    suspend fun syncClock(): Boolean

    /** Enable continuous monitoring so the device records history (e.g. ring SpO2/temp/HRV). */
    suspend fun enableMonitoring() {}

    /** Every stream this device can be asked for, in pull order (e.g. "ring.history.all"). */
    fun streams(): List<String>

    /** Pull one stream verbatim. Null when unsupported / not ready; empty rows when nothing stored. */
    suspend fun pullStream(stream: String): RawStream?

    /**
     * Start forwarding everything the device pushes on its own (live vitals, waveforms, alarms,
     * device-initiated events) to [sink] until [stopLive]. Called on every READY transition.
     */
    fun startLive(sink: (stream: String, payload: Map<String, Any?>) -> Unit) {}

    fun stopLive() {}

    /** On-demand measurements this device can take now (buttons in the UI). */
    fun measurableMetrics(): List<Metric>

    /** Trigger an on-demand measurement; returns readings (stored with Source.MEASUREMENT). */
    suspend fun measure(metric: Metric): List<Reading>

    /** High-rate raw signals this device can record (ECG / PPG / accelerometer). */
    fun recordableSignals(): List<Metric>

    /**
     * Record a raw waveform for [durationMs]; the engine writes samples to a file. Requires READY.
     * [onSamples] sees each batch as it arrives (live waveform). Cancelling stops the device.
     */
    suspend fun recordSignal(metric: Metric, durationMs: Long, onSamples: (List<Double>) -> Unit = {}): RawCapture?
}

/** A recorded raw waveform: batches of samples (as delivered) + the measured effective rate. */
data class RawCapture(
    val metric: Metric,
    val deviceId: String,
    val batches: List<Batch>,
    val measuredHz: Double,
) {
    data class Batch(val tsMs: Long, val seq: Long, val values: List<Double>)
    val sampleCount: Long get() = batches.sumOf { it.values.size.toLong() }
}

/**
 * One stream pull: vendor rows kept verbatim (archived and stored before parsing). Each row should
 * carry `tsMs` (epoch ms) when the adapter can derive the record's own timestamp.
 */
data class RawStream(
    val stream: String,
    val deviceId: String,
    val rows: List<Map<String, Any?>>,
)
