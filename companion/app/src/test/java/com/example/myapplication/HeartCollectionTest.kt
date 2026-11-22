package com.example.myapplication

import com.example.myapplication.heart.HeartCsv
import com.example.myapplication.adapter.StandardHeartAdapter
import org.junit.Assert.*
import org.junit.Test

class HeartCollectionTest {
    @Test fun canonicalAndNativeExportsAgree() {
        val expected="timestamp,heart_rate\n2026-11-17T10:00:00Z,68\n"
        assertEquals(expected, HeartCsv.canonical(expected.toByteArray()).toString(Charsets.UTF_8))
        val native="ts_utc,value,metric,unit,device_id,notes\n2026-11-17T10:00:00Z,68.0,HEART_RATE,bpm,ET488,\"one, sample\"\n"
        assertEquals(expected, HeartCsv.canonical(native.toByteArray()).toString(Charsets.UTF_8))
    }
    @Test fun rejectsFractionalBpm() {
        assertThrows(IllegalArgumentException::class.java) { HeartCsv.canonical("timestamp,heart_rate\n2026-11-17T10:00:00Z,68.5".toByteArray()) }
    }
    @Test fun rejectsMixedDevices() {
        val file="ts_utc,value,metric,unit,device_id\n2026-11-17T10:00:00Z,68,HEART_RATE,bpm,A\n2026-11-17T10:01:00Z,69,HEART_RATE,bpm,B"
        assertThrows(IllegalArgumentException::class.java) { HeartCsv.canonical(file.toByteArray()) }
    }
    @Test fun rejectsBrokenQuotesAndDuplicateHeaders() {
        listOf("timestamp,heart_rate\n\"unfinished,60", "timestamp,timestamp\na,b").forEach { csv ->
            assertThrows(IllegalArgumentException::class.java) { HeartCsv.canonical(csv.toByteArray()) }
        }
    }
    @Test fun standardHeartRateUsesFlagsAndUnsignedBytes() {
        assertEquals(200, StandardHeartAdapter.decode(byteArrayOf(0,200.toByte())))
        assertEquals(300, StandardHeartAdapter.decode(byteArrayOf(1,44,1)))
        assertNull(StandardHeartAdapter.decode(byteArrayOf(1,44)))
        assertNull(StandardHeartAdapter.decode(byteArrayOf(0,0)))
        assertNull(StandardHeartAdapter.decode(byteArrayOf(1,45,1)))
    }
}
