package com.example.myapplication.adapter

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.os.Build
import com.example.myapplication.core.DeviceKind
import com.example.myapplication.core.Metric
import com.example.myapplication.core.Reading
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import kotlin.coroutines.resume

/** Bluetooth SIG Heart Rate Service only. No guessed proprietary watch commands. */
@SuppressLint("MissingPermission")
class StandardHeartAdapter(private val context: Context): DeviceAdapter {
    override val kind=DeviceKind.UNKNOWN
    private val mutable=MutableStateFlow(LinkState.IDLE)
    override val state=mutable.asStateFlow()
    override var currentMac:String?=null;private set
    private var gatt:BluetoothGatt?=null
    private val latest=MutableStateFlow<Reading.Scalar?>(null)
    private var ready:((Boolean)->Unit)?=null
    override fun isReady()=mutable.value==LinkState.READY

    override suspend fun connect(mac:String):Boolean {
        disconnect();currentMac=mac;mutable.value=LinkState.CONNECTING
        val result=withTimeoutOrNull(20_000L){suspendCancellableCoroutine<Boolean>{continuation->
            ready={ok->if(continuation.isActive)continuation.resume(ok)}
            continuation.invokeOnCancellation{disconnect()}
            try{val adapter=(context.getSystemService(Context.BLUETOOTH_SERVICE)as BluetoothManager).adapter
                gatt=adapter.getRemoteDevice(mac).connectGatt(context,false,callback,BluetoothDevice.TRANSPORT_LE)
            }catch(e:Exception){complete(false)}
        }}?:false
        if(!result)disconnect()
        return result
    }
    private fun complete(ok:Boolean){mutable.value=if(ok)LinkState.READY else LinkState.ERROR;ready?.invoke(ok);ready=null}
    private val callback=object:BluetoothGattCallback(){
        override fun onConnectionStateChange(connection:BluetoothGatt,status:Int,newState:Int){
            if(status==BluetoothGatt.GATT_SUCCESS&&newState==BluetoothProfile.STATE_CONNECTED)connection.discoverServices()
            else if(newState==BluetoothProfile.STATE_DISCONNECTED){mutable.value=LinkState.DISCONNECTED;ready?.invoke(false);ready=null;connection.close()}
            else if(status!=BluetoothGatt.GATT_SUCCESS)complete(false)
        }
        @Suppress("DEPRECATION")
        override fun onServicesDiscovered(connection:BluetoothGatt,status:Int){
            if(status!=BluetoothGatt.GATT_SUCCESS){complete(false);return}
            val characteristic=connection.getService(SERVICE)?.getCharacteristic(MEASUREMENT)
            val descriptor=characteristic?.getDescriptor(CCC)
            if(characteristic==null||descriptor==null||!connection.setCharacteristicNotification(characteristic,true)){complete(false);return}
            val bytes=BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            if(Build.VERSION.SDK_INT>=33){if(connection.writeDescriptor(descriptor,bytes)!=BluetoothStatusCodes.SUCCESS)complete(false)}
            else { descriptor.value=bytes; if(!connection.writeDescriptor(descriptor))complete(false) }
        }
        override fun onDescriptorWrite(connection:BluetoothGatt,descriptor:BluetoothGattDescriptor,status:Int){if(descriptor.uuid==CCC)complete(status==BluetoothGatt.GATT_SUCCESS)}
        @Deprecated("Legacy Android callback")
        override fun onCharacteristicChanged(connection:BluetoothGatt,characteristic:BluetoothGattCharacteristic){
            @Suppress("DEPRECATION") if(characteristic.uuid==MEASUREMENT)accept(characteristic.value)
        }
        override fun onCharacteristicChanged(connection:BluetoothGatt,characteristic:BluetoothGattCharacteristic,value:ByteArray){if(characteristic.uuid==MEASUREMENT)accept(value)}
    }
    private fun accept(bytes:ByteArray?){decode(bytes)?.let{latest.value=Reading.Scalar(Metric.HEART_RATE,System.currentTimeMillis(),it.toDouble(),"bpm")}}
    override fun disconnect(){ready?.invoke(false);ready=null;runCatching{gatt?.disconnect();gatt?.close()};gatt=null;latest.value=null;mutable.value=LinkState.IDLE}
    override suspend fun readDeviceInfo()=if(isReady())DeviceInfo(currentMac!!,kind,runCatching{gatt?.device?.name}.getOrNull()?:"Bluetooth Heart Rate",null,null,mapOf("heart_rate" to true))else null
    override suspend fun syncClock()=false
    override fun streams()=emptyList<String>()
    override suspend fun pullStream(stream:String):RawStream?=null
    override fun measurableMetrics()=listOf(Metric.HEART_RATE)
    override suspend fun measure(metric:Metric):List<Reading>{
        if(metric!=Metric.HEART_RATE||!isReady())return emptyList()
        val after=latest.value?.deviceTsMs?:0L
        return withTimeoutOrNull(12_000L){listOf(latest.filterNotNull().first{it.deviceTsMs>after})}?:emptyList()
    }
    override fun recordableSignals()=emptyList<Metric>()
    override suspend fun recordSignal(metric:Metric,durationMs:Long,onSamples:(List<Double>)->Unit):RawCapture?=null
    companion object {
        val SERVICE:UUID=UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        private val MEASUREMENT=UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        private val CCC=UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        fun decode(bytes:ByteArray?):Int?{
            if(bytes==null||bytes.size<2)return null
            val wide=(bytes[0].toInt()and 1)!=0
            if(wide&&bytes.size<3)return null
            val value=(bytes[1].toInt()and 255)+if(wide)((bytes[2].toInt()and 255)shl 8)else 0
            return value.takeIf{it in 1..300}
        }
    }
}
