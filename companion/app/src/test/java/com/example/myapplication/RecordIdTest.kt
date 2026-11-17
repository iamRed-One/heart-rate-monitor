package com.example.myapplication

import com.example.myapplication.core.Metric
import com.example.myapplication.core.RecordId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Pure-JVM proof that record identity is deterministic (so re-sync de-duplicates). */
class RecordIdTest {

    @Test
    fun sameCoordinates_sameId() {
        val a = RecordId.of("ring-1", Metric.HEART_RATE, 1_700_000_000_000L)
        val b = RecordId.of("ring-1", Metric.HEART_RATE, 1_700_000_000_000L)
        assertEquals(a, b)
    }

    @Test
    fun differentTimestamp_differentId() {
        val a = RecordId.of("ring-1", Metric.HEART_RATE, 1_700_000_000_000L)
        val b = RecordId.of("ring-1", Metric.HEART_RATE, 1_700_000_001_000L)
        assertNotEquals(a, b)
    }

    @Test
    fun differentDeviceOrMetric_differentId() {
        val base = RecordId.of("ring-1", Metric.HEART_RATE, 1_700_000_000_000L)
        assertNotEquals(base, RecordId.of("watch-1", Metric.HEART_RATE, 1_700_000_000_000L))
        assertNotEquals(base, RecordId.of("ring-1", Metric.SPO2, 1_700_000_000_000L))
    }

    @Test
    fun discriminator_separatesColocatedRecords() {
        val s1 = RecordId.of("watch-1", Metric.SLEEP, 1_700_000_000_000L, "stage-0")
        val s2 = RecordId.of("watch-1", Metric.SLEEP, 1_700_000_000_000L, "stage-1")
        assertNotEquals(s1, s2)
    }

    @Test
    fun id_isSha256Hex() {
        val id = RecordId.of("ring-1", Metric.HEART_RATE, 1L)
        assertEquals(64, id.length)
        assert(id.all { it in '0'..'9' || it in 'a'..'f' })
    }
}
