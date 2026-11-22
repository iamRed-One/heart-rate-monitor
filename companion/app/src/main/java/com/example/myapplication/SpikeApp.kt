package com.example.myapplication

import android.app.Application
import com.example.myapplication.sync.SyncEngine
import com.yucheng.ycbtsdk.YCBTClient

class SpikeApp : Application() {
    val engine: SyncEngine by lazy {
        YCBTClient.initClient(this, true, BuildConfig.DEBUG)
        SyncEngine(this)
    }
}
