package com.example.myapplication.sync

import com.example.myapplication.core.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.min

/**
 * Serialises all commands to one device — the vendor SDKs process only one operation at a time, so
 * every device interaction (connect, sync, measurement) must go through here. Adds bounded retry
 * with exponential backoff. One queue per device.
 */
class CommandQueue(private val name: String) {

    private val mutex = Mutex()

    /**
     * Run [block] exclusively for this device, retrying up to [retries] times on exception.
     * Returns the block's result, or null if all attempts failed.
     */
    suspend fun <T> run(label: String, retries: Int = 2, block: suspend () -> T): T? = mutex.withLock {
        var attempt = 0
        while (true) {
            try {
                return@withLock block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                attempt++
                Log.w("queue", "$name/$label attempt $attempt failed: ${e.javaClass.name}: ${e.message}", e)
                if (attempt > retries) return@withLock null
                delay(backoff(attempt))
            }
        }
        @Suppress("UNREACHABLE_CODE") null
    }

    companion object {
        /** Exponential backoff: 1s, 2s, 4s… capped at 30s. */
        fun backoff(attempt: Int): Long = min(1000L shl (attempt - 1).coerceIn(0, 5), 30_000L)
    }
}
