package com.example.myapplication.sync

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.myapplication.MainActivity
import com.example.myapplication.R

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Foreground service that keeps the process alive so the [SyncEngine] can hold connections and sync
 * with the screen off. The engine itself lives in the Application; this service just owns the
 * ongoing notification (M2 milestone: stay connected through a long-running test).
 */
class SyncService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        _running.value = false
        val collector = (application as com.example.myapplication.SpikeApp).engine.collector
        if (collector.state.value.active) collector.stop()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { (application as com.example.myapplication.SpikeApp).engine.collector.stop();stopForegroundCompat(); stopSelf(); _running.value = false }
            else -> { _running.value = startForegroundCompat() }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundCompat(): Boolean {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mgr.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID, "Sync",
                    if (QUIET_NOTIFICATION) NotificationManager.IMPORTANCE_MIN else NotificationManager.IMPORTANCE_LOW,
                )
            )
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val n: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(if((application as com.example.myapplication.SpikeApp).engine.collector.state.value.active) "HeaRt is recording" else "HeaRt is connected")
            .setContentText("Keeping your wearable connected. Open HeaRt to manage collection.")
            .addAction(0, "Stop recording", PendingIntent.getService(this, 1, Intent(this, SyncService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        // Android refuses a connected-device service without the Bluetooth permission; stop quietly instead of crashing.
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            } else {
                startForeground(NOTIF_ID, n)
            }
            true
        } catch (e: SecurityException) {
            stopSelf()
            false
        }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE)
        else @Suppress("DEPRECATION") stopForeground(true)
    }

    companion object {
        /**
         * Android requires an ongoing notification for a foreground service, but a quiet (minimised)
         * channel keeps it out of the way. A channel's importance can't change after it is created,
         * so the quiet variant uses its own channel id.
         */
        const val QUIET_NOTIFICATION = false
        private val CHANNEL_ID = if (QUIET_NOTIFICATION) "sync_quiet" else "sync"
        private const val NOTIF_ID = 2001
        const val ACTION_STOP = "com.example.myapplication.sync.STOP"

        private val _running = MutableStateFlow(false)
        /** Whether background collection is on (drives the Data-screen switch). */
        val running: StateFlow<Boolean> = _running.asStateFlow()

        fun start(context: Context) {
            val i = Intent(context, SyncService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(i)
            else context.startService(i)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SyncService::class.java))
        }
    }
}
