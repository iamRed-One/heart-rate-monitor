package com.example.myapplication.heart

import com.example.myapplication.adapter.WatchAdapter
import com.example.myapplication.core.DeviceKind
import com.example.myapplication.core.Metric
import com.example.myapplication.core.Reading
import com.example.myapplication.sync.ConnectionManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class MonitorState(
    val active: Boolean = false,
    val connected: Boolean = false,
    val kind: DeviceKind? = null,
    val bpm: Int? = null,
    val lastAt: Long = 0,
    val message: String = "Connect your watch to begin",
) {
    fun isFresh(now: Long) = connected && bpm != null && now - lastAt in 0..10_000
}

data class DeviceSample(val kind: DeviceKind, val reading: Reading.Scalar)

/** Connection is passive; measurement starts only after an explicit read or recording. */
class HeartMonitor(private val scope: CoroutineScope, private val connection: (DeviceKind) -> ConnectionManager?) {
    private val mutable = MutableStateFlow(MonitorState())
    val state = mutable.asStateFlow()
    private val events = MutableSharedFlow<DeviceSample>(extraBufferCapacity = 256)
    val samples = events.asSharedFlow()
    private var job: Job? = null
    private var continuous = false
    @Volatile private var generation = 0

    @Synchronized fun connected(kind: DeviceKind) {
        if (mutable.value.kind != kind) {
            stop()
            mutable.value = MonitorState(connected = true, kind = kind, message = "Ready to read heart rate")
        } else mutable.update { it.copy(connected = true) }
    }

    @Synchronized fun start(kind: DeviceKind, continuous: Boolean = false) {
        if (job?.isActive == true && mutable.value.kind == kind && this.continuous == continuous) return
        val previous = job
        val run = ++generation
        this.continuous = continuous
        previous?.cancel()
        val current = mutable.value.takeIf { it.kind == kind } ?: MonitorState(kind = kind)
        mutable.value = current.copy(active = true, connected = connection(kind)?.isReady == true,
            message = if (continuous) "Recording heart rate" else "Reading heart rate...")
        job = scope.launch {
            previous?.join()
            var received = false
            try {
                do {
                    val manager = connection(kind)
                    if (manager == null || !manager.isReady) {
                        if (!continuous) break
                        mutable.update { it.copy(connected = false, message = "Waiting for your watch to reconnect") }
                        delay(1000)
                        continue
                    }
                    mutable.update { it.copy(connected = true) }
                    val result = withTimeoutOrNull(if (continuous) 40_000L else 30_000L) {
                        manager.execute("read-heart-rate") { adapter ->
                            if (continuous && adapter is WatchAdapter) {
                                adapter.captureHeartRate(30_000L) { received = publish(run, kind, it) || received }
                            } else {
                                adapter.measure(Metric.HEART_RATE).filterIsInstance<Reading.Scalar>()
                                    .filter { it.metric == Metric.HEART_RATE }
                                    .forEach { received = publish(run, kind, it) || received }
                            }
                        }
                    }
                    if (!continuous) break
                    delay(500)
                } while (isActive)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (run == generation) mutable.update { it.copy(message = "Reading interrupted. Try again.") }
            } finally {
                if (run == generation) mutable.update { it.copy(active = false,
                    connected = connection(kind)?.isReady == true,
                    message = if (received) "Heart rate measured" else "No reading received. Adjust the fit and try again.") }
            }
        }
    }

    private fun publish(run: Int, kind: DeviceKind, reading: Reading.Scalar): Boolean {
        if (run != generation || connection(kind)?.isReady != true ||
            reading.value !in 1.0..300.0 || reading.value % 1.0 != 0.0) return false
        mutable.update { it.copy(bpm = reading.value.toInt(), lastAt = System.currentTimeMillis()) }
        if (!events.tryEmit(DeviceSample(kind, reading))) stop("Readings could not be saved quickly enough. Try again.")
        return true
    }

    @Synchronized fun stop(message: String = "Reading stopped") {
        generation++
        job?.cancel()
        mutable.update { it.copy(active = false, message = message) }
    }
    suspend fun awaitStopped() { job?.join() }
    fun disconnected() {
        stop("Watch disconnected")
        mutable.update { it.copy(connected = false) }
    }
}
