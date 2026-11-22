package com.example.myapplication

import com.example.myapplication.adapter.*
import com.example.myapplication.core.*
import com.example.myapplication.heart.*
import com.example.myapplication.sync.ConnectionManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class HeartMonitorTest {
    private class Sensor(val value: Double = 72.0) : DeviceAdapter {
        override val kind = DeviceKind.UNKNOWN
        override val state = MutableStateFlow(LinkState.READY)
        override val currentMac = "sample-sensor"
        val calls = AtomicInteger()
        val concurrent = AtomicInteger()
        val maxConcurrent = AtomicInteger()
        override fun isReady() = state.value == LinkState.READY
        override suspend fun connect(mac: String) = true
        override fun disconnect() { state.value = LinkState.DISCONNECTED }
        override suspend fun readDeviceInfo(): DeviceInfo? = null
        override suspend fun syncClock() = true
        override fun streams() = emptyList<String>()
        override suspend fun pullStream(stream: String): RawStream? = null
        override fun measurableMetrics() = listOf(Metric.HEART_RATE)
        override suspend fun measure(metric: Metric): List<Reading> {
            calls.incrementAndGet()
            val count = concurrent.incrementAndGet()
            maxConcurrent.updateAndGet { maxOf(it, count) }
            return try { delay(20); listOf(Reading.Scalar(metric, System.currentTimeMillis(), value, "bpm")) }
                finally { concurrent.decrementAndGet() }
        }
        override fun recordableSignals() = emptyList<Metric>()
        override suspend fun recordSignal(metric: Metric, durationMs: Long, onSamples: (List<Double>) -> Unit): RawCapture? = null
    }
    @Test fun readsWithoutAnAccountOrRecording() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val sensor = Sensor(); val manager = ConnectionManager(sensor, scope)
            val monitor = HeartMonitor(scope) { manager }
            val next = async(start = CoroutineStart.UNDISPATCHED) { monitor.samples.first() }
            monitor.start(DeviceKind.UNKNOWN)
            val sample = withTimeout(3000) { next.await() }
            assertEquals(72.0, sample.reading.value, 0.0)
            assertEquals(DeviceKind.UNKNOWN, sample.kind)
            assertTrue(monitor.state.value.isFresh(System.currentTimeMillis()))
        } finally { scope.cancel() }
    }
    @Test fun repeatedReadyEventsKeepOneLoopAndStopCancelsIt() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val sensor = Sensor(); val manager = ConnectionManager(sensor, scope)
            val monitor = HeartMonitor(scope) { manager }
            monitor.start(DeviceKind.UNKNOWN, continuous = true); monitor.start(DeviceKind.UNKNOWN, continuous = true)
            withTimeout(3000) { while (sensor.calls.get() < 2) delay(20) }
            monitor.stop()
            assertFalse(monitor.state.value.active)
            assertEquals(1, sensor.maxConcurrent.get())
            val calls = sensor.calls.get(); delay(600); assertEquals(calls, sensor.calls.get())
        } finally { scope.cancel() }
    }
    @Test fun invalidSensorValuesAreNotShown() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val sensor = Sensor(Double.NaN); val manager = ConnectionManager(sensor, scope)
            val monitor = HeartMonitor(scope) { manager }
            monitor.start(DeviceKind.UNKNOWN)
            withTimeout(3000) { monitor.state.first { !it.active } }
            assertNull(monitor.state.value.bpm)
            assertFalse(monitor.state.value.isFresh(System.currentTimeMillis()))
        } finally { scope.cancel() }
    }
    @Test fun staleDisconnectedAndFutureReadingsAreNotFresh() {
        val state = MonitorState(true, true, DeviceKind.UNKNOWN, 72, 10000)
        assertTrue(state.isFresh(20000)); assertFalse(state.isFresh(20001)); assertFalse(state.isFresh(9999))
        assertFalse(state.copy(connected = false).isFresh(10000))
        assertTrue(state.copy(active = false).isFresh(10000))
    }

    @Test fun connectingNeverStartsTheSensor() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val sensor = Sensor(); val manager = ConnectionManager(sensor, scope)
            val monitor = HeartMonitor(scope) { manager }
            monitor.connected(DeviceKind.UNKNOWN)
            delay(150)
            assertEquals(0, sensor.calls.get())
            assertTrue(monitor.state.value.connected)
            assertFalse(monitor.state.value.active)
        } finally { scope.cancel() }
    }
    @Test fun oneReadFinishesAndRetainsItsValue() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val sensor = Sensor(); val manager = ConnectionManager(sensor, scope)
            val monitor = HeartMonitor(scope) { manager }
            monitor.connected(DeviceKind.UNKNOWN)
            monitor.start(DeviceKind.UNKNOWN)
            val result = withTimeout(3000) { monitor.state.first { !it.active && it.bpm != null } }
            delay(600)
            assertEquals(1, sensor.calls.get())
            assertEquals(0, sensor.concurrent.get())
            assertEquals(72, result.bpm)
            assertTrue(result.connected)
            assertTrue(result.isFresh(System.currentTimeMillis()))
            monitor.stop()
            assertEquals(72, monitor.state.value.bpm)
        } finally { scope.cancel() }
    }
}
