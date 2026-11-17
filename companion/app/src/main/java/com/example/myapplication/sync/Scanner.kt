package com.example.myapplication.sync

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult as AndroidScanResult
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.example.myapplication.core.DeviceKind
import com.example.myapplication.core.Log
import com.yucheng.ycbtsdk.YCBTClient
import com.yucheng.ycbtsdk.bean.ScanDeviceBean
import com.yucheng.ycbtsdk.response.BleScanResponse

data class ScanResult(val name: String, val mac: String, val rssi: Int, val kind: DeviceKind)

/**
 * Finds both devices. The Yucheng scan surfaces the ring (Q520); the ET488 watch advertises as a
 * Veepoo/Realtek device and is only seen by a plain Android BLE scan — so we run both and merge.
 * The watch uses a rotating address, so a result is only valid briefly: connect promptly.
 */
class Scanner(context: Context) {

    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var rawCallback: ScanCallback? = null

    private fun kindFor(name: String?): DeviceKind = when {
        name == null -> DeviceKind.UNKNOWN
        name.startsWith(RING_PREFIX, true) -> DeviceKind.RING
        name.startsWith(WATCH_PREFIX, true) -> DeviceKind.WATCH
        else -> DeviceKind.UNKNOWN
    }

    @SuppressLint("MissingPermission")
    fun start(seconds: Int = 8, onResult: (ScanResult) -> Unit) {
        // Yucheng scan (ring).
        YCBTClient.startScanBle(object : BleScanResponse {
            override fun onScanResponse(code: Int, device: ScanDeviceBean?) {
                device ?: return
                val kind = kindFor(device.deviceName)
                if (kind == DeviceKind.UNKNOWN) return
                val mac = device.deviceMac ?: return
                onResult(ScanResult(device.deviceName ?: "?", mac, device.deviceRssi, kind))
            }
        }, seconds)

        // Raw Android scan (watch, and ring as backup).
        val scanner = (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)
            ?.adapter?.bluetoothLeScanner
        if (scanner != null) {
            val cb = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: AndroidScanResult) {
                    val standard = result.scanRecord?.serviceUuids?.any { it.uuid == com.example.myapplication.adapter.StandardHeartAdapter.SERVICE } == true
                    val name = result.device?.name ?: result.scanRecord?.deviceName ?: if(standard) "Bluetooth Heart Rate" else return
                    val kind = kindFor(name)
                    if (kind == DeviceKind.UNKNOWN && !standard) return
                    val mac = result.device?.address ?: return
                    onResult(ScanResult(name, mac, result.rssi, kind))
                }
            }
            rawCallback = cb
            scanner.startScan(cb)
            handler.postDelayed({ stop() }, seconds * 1000L)
        }
        Log.d("scan", "scan start (${seconds}s)")
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        runCatching { YCBTClient.stopScanBle() }
        val cb = rawCallback ?: return
        rawCallback = null
        (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)
            ?.adapter?.bluetoothLeScanner?.stopScan(cb)
    }

    companion object {
        const val RING_PREFIX = "Q520"
        const val WATCH_PREFIX = "ET488"
    }
}
