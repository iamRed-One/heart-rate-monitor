package com.example.myapplication.sync

import com.example.myapplication.adapter.DeviceAdapter
import com.example.myapplication.adapter.LinkState
import com.example.myapplication.core.DeviceKind
import com.example.myapplication.core.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Keeps one device connected. Holds the device's serial [CommandQueue] and drives connect, with
 * automatic reconnect (exponential backoff) on unexpected drops. A user-initiated disconnect clears
 * the desired MAC so no reconnect is attempted. All device work runs through [execute], keeping the
 * single-operation contract across connect, reconnect and collection commands.
 */
class ConnectionManager(
    val adapter: DeviceAdapter,
    private val scope: CoroutineScope,
) {
    val kind: DeviceKind get() = adapter.kind
    val state: StateFlow<LinkState> get() = adapter.state
    val queue = CommandQueue(adapter.kind.name)

    @Volatile private var desiredMac: String? = null   // null ⇒ user wants it disconnected
    @Volatile private var reconnecting = false
    @Volatile private var userPaused = false   // the patient chose to disconnect; the supervisor must leave it alone
    val isUserPaused: Boolean get() = userPaused

    /** An explicit Reconnect overrides an earlier disconnect. */
    fun clearUserPause() { userPaused = false }

    /** True from a connect request until it succeeds or gives up, including reconnect backoff. */
    private val _connecting = MutableStateFlow(false)
    val connecting: StateFlow<Boolean> = _connecting.asStateFlow()

    /** The last connect attempt ran out of time or failed, and nothing is retrying. */
    private val _failed = MutableStateFlow(false)
    val failed: StateFlow<Boolean> = _failed.asStateFlow()

    init {
        // React to unexpected drops.
        scope.launch {
            adapter.state.collect { s ->
                if (s == LinkState.DISCONNECTED && desiredMac != null && !reconnecting) {
                    scope.launch { reconnectLoop() }
                }
            }
        }
    }

    /** Ask to connect and stay connected to [mac]. */
    fun requestConnect(mac: String) {
        desiredMac = mac
        userPaused = false
        // Only skip when the app agrees the link is up; the SDK can say "ready" while our state lags.
        if (adapter.isReady() && adapter.state.value == LinkState.READY) return
        _connecting.value = true
        _failed.value = false
        scope.launch {
            val ok = try { queue.run("connect") { adapter.connect(mac) } == true || adapter.isReady() } finally { _connecting.value = false }
            if (!ok && desiredMac == mac) _failed.value = true
        }
    }

    /** User-initiated disconnect; suppresses auto-reconnect. */
    fun requestDisconnect() {
        desiredMac = null
        userPaused = true
        _failed.value = false
        adapter.disconnect()
    }

    val isReady: Boolean get() = adapter.isReady()

    /** Run a device command serialised behind connect/reconnect; connects first if needed. */
    suspend fun <T> execute(label: String, block: suspend (DeviceAdapter) -> T): T? {
        val mac = desiredMac
        if (!adapter.isReady() && mac != null) queue.run("connect") { adapter.connect(mac) }
        if (!adapter.isReady()) { Log.w("conn", "${kind} not ready for $label"); return null }
        return queue.run(label) { block(adapter) }
    }

    private suspend fun reconnectLoop() {
        if (reconnecting) return
        reconnecting = true
        _connecting.value = true
        _failed.value = false
        var attempt = 0
        try {
            while (desiredMac != null && !adapter.isReady() && attempt < MAX_RECONNECT_ATTEMPTS) {
                attempt++
                delay(CommandQueue.backoff(attempt))
                val mac = desiredMac ?: break
                Log.i("conn", "${kind} reconnect attempt $attempt")
                queue.run("reconnect") { adapter.connect(mac) }
            }
        } finally {
            reconnecting = false
            _connecting.value = false
            if (desiredMac != null && !adapter.isReady()) _failed.value = true
        }
    }

    private companion object {
        const val MAX_RECONNECT_ATTEMPTS = 30
    }
}
