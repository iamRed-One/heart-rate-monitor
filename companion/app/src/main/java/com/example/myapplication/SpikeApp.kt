package com.example.myapplication

import android.app.Application
import com.example.myapplication.sync.SyncEngine
import com.yucheng.ycbtsdk.YCBTClient

/** Owns HeaRt's device connections and collection lifecycle. */
class SpikeApp : Application() {
    lateinit var engine: SyncEngine
        private set
    override fun onCreate() {
        super.onCreate()
        YCBTClient.initClient(this, true, BuildConfig.DEBUG)
        engine = SyncEngine(this)
    }
}
