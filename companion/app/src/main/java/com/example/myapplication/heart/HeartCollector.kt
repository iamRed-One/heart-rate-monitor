package com.example.myapplication.heart

import android.content.Context
import com.example.myapplication.adapter.WatchAdapter
import com.example.myapplication.core.DeviceKind
import com.example.myapplication.core.Metric
import com.example.myapplication.core.Reading
import com.example.myapplication.core.Time
import com.example.myapplication.sync.ConnectionManager
import com.example.myapplication.sync.SyncService
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject

data class CollectionState(val active: Boolean=false, val message: String="Ready to record", val sent: Int=0, val captured: Int=0, val bpm: Int?=null, val lastAt: Long=0, val recordingId: String?=null)

class HeartCollector(private val context: Context, private val scope: CoroutineScope,
    private val connection: (DeviceKind)->ConnectionManager?) {
    val store = HeartStore(context)
    private val mutable = MutableStateFlow(CollectionState())
    val state = mutable.asStateFlow()
    private var job: Job?=null
    private var uploadToken: String?=null
    private var owner=""
    private var recording=""

    fun start(kind: DeviceKind, pairingCode: String = "") {
        if (job?.isActive==true) return
        val cm=connection(kind)
        if(cm==null || !cm.isReady) { mutable.update{it.copy(message="Connect the selected device first.")}; return }
        owner=HeartApi.account.value.id
        if(owner.isEmpty()) { mutable.update{it.copy(message="Sign in before recording.")}; return }
        job=scope.launch {
            mutable.value=CollectionState(active=true,message="Starting your recording…")
            var paired=false
            try {
                withContext(Dispatchers.Main) { SyncService.start(context) }
                val created=if(pairingCode.isBlank()) HeartApi.call("/live/sessions","POST",JSONObject().put("save_recording",true)) else JSONObject().put("pairing_code",pairingCode.trim())
                recording=created.optString("id")
                val pairedData=HeartApi.call("/live/pair","POST",JSONObject().put("code",created.getString("pairing_code"))
                    .put("device_id",cm.adapter.currentMac ?: kind.name).put("device_label",when(kind){DeviceKind.WATCH->"ET488 · HeaRt";DeviceKind.RING->"Q520 · HeaRt";else->"Bluetooth Heart Rate · HeaRt"}))
                recording=pairedData.getString("session_id");uploadToken=pairedData.getString("upload_token");paired=true
                mutable.update{it.copy(recordingId=recording,message="Recording until you press Stop")}
                coroutineScope {
                    val incoming=Channel<Reading.Scalar>(256)
                    val writer=launch(Dispatchers.IO + NonCancellable) {
                        for(sample in incoming) {
                            store.add(owner,recording,Time.utcIso(sample.deviceTsMs),sample.value.toInt())
                            mutable.update{it.copy(captured=it.captured+1,bpm=sample.value.toInt(),lastAt=System.currentTimeMillis())}
                        }
                    }
                    val uploader=launch { while(isActive) { try { flush(); delay(500) } catch(e: ApiFailure) {
                        if(e.status in listOf(401,409,410)) throw e
                        mutable.update{it.copy(message="Upload interrupted. Readings are kept on this phone; retrying…")};delay(3000)
                    } catch(e: java.io.IOException) { mutable.update{it.copy(message="Offline. Recording locally; upload will retry.")};delay(3000) } } }
                    val control=launch {
                        while(isActive) {
                            try { if(!HeartApi.call("/live/sessions/$recording/control",bearer=uploadToken).getBoolean("active")) throw RecordingStopped() }
                            catch(e: java.io.IOException) { /* keep local capture during a brief network outage */ }
                            delay(2000)
                        }
                    }
                    try {
                        while(isActive) {
                            if(!cm.isReady) { mutable.update{it.copy(message="Device disconnected. Reconnecting…")}; delay(2000) }
                            cm.execute("heart-live") { adapter ->
                                if(adapter is WatchAdapter) adapter.captureHeartRate(30_000L) { sample ->
                                    if(incoming.trySend(sample).isFailure) mutable.update{it.copy(message="Local buffer full. Stop and check the phone.")}
                                } else adapter.measure(Metric.HEART_RATE).filterIsInstance<Reading.Scalar>().filter{it.metric==Metric.HEART_RATE}.forEach { incoming.send(it) }
                            }
                            delay(500)
                        }
                    } finally {
                        incoming.close(); withContext(NonCancellable) { withTimeoutOrNull(3000) { writer.join() } }
                        uploader.cancel();control.cancel()
                    }
                }
            } catch(e: CancellationException) {
                mutable.update{it.copy(message="Recording stopped. Saved readings remain available.")}
                throw e
            } catch(e: Exception) { mutable.update{it.copy(message=e.message ?: "Recording stopped. Check your connection.")} }
            finally {
                if(paired) withContext(NonCancellable) {
                    withTimeoutOrNull(12000) { runCatching { flush() };runCatching { HeartApi.call("/live/sessions/$recording/finish","POST",bearer=uploadToken) } }
                }
                mutable.update{it.copy(active=false)}
                withContext(Dispatchers.Main) { SyncService.stop(context) }
            }
        }
    }
    fun stop(){job?.cancel()}
    fun retry(){if(uploadToken!=null)scope.launch { try {flush();mutable.update{it.copy(message="Pending upload checked.")}}catch(e:Exception){mutable.update{it.copy(message=e.message ?: "Upload unavailable. Export your local CSV for recovery.")}} }}
    private suspend fun flush() {
        val token=uploadToken ?: return
        val rows=withContext(Dispatchers.IO){store.rows(owner,recording,pending=true,limit=30)}
        for(row in rows) {
            HeartApi.call("/live/sessions/$recording/readings","POST",JSONObject().put("sample_id",row.id).put("timestamp",row.timestamp).put("heart_rate",row.bpm),token)
            withContext(Dispatchers.IO){store.mark(row.id)};mutable.update{it.copy(sent=it.sent+1)}
            delay(110)
        }
    }
    private class RecordingStopped: Exception("Stopped from the web viewer. Your readings have been saved.")
}
