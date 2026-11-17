package com.example.myapplication.core

import java.security.MessageDigest

/**
 * Canonical, vendor-independent domain model for Phase 1. Nothing here references a device SDK.
 * These enums and the [Provenance] value object are embedded into the Room entities (see
 * `com.example.myapplication.db`), which are the source of truth.
 */

/** The metrics the app can collect (see the Phase 1 metric inventory). */
enum class Metric {
    HEART_RATE, BLOOD_PRESSURE, SPO2, TEMPERATURE, HRV, RESPIRATORY_RATE,
    SLEEP, SPORT, ECG, GLUCOSE, BLOOD_COMPONENTS, BODY_COMPOSITION,
    PPG, PPI, ACCELEROMETER, STRESS,
}

/** Which device a record came from. */
enum class DeviceKind { RING, WATCH, UNKNOWN }

/** How a value was obtained — alters how it should be interpreted. */
enum class Source { HISTORY, REALTIME, MEASUREMENT, DEVICE_INITIATED }

/** Trust marker. UNVALIDATED/ESTIMATED values must never drive an alert (later phases). */
enum class Quality { OK, SUSPECT, UNWORN, ESTIMATED }

/** Kinds of record that change the meaning of other values and are stored in their own right. */
enum class EventType { CONFIG_SNAPSHOT, CALIBRATION, CLOCK_SYNC }

/**
 * Common provenance carried by every record. Stored inline (Room `@Embedded`) on each entity.
 * [recordId] is the entity primary key and is computed deterministically (see [RecordId]) so a
 * re-sync of the same device datum produces the same id and de-duplicates on insert.
 */
data class Provenance(
    val participantId: String,
    val deviceId: String,
    val deviceModel: String,
    val firmware: String?,
    val tsUtc: String,          // ISO-8601 UTC, e.g. 2026-10-22T10:34:47.102Z
    val tzOffsetMin: Int,       // local offset from UTC in minutes
    val deviceTsRaw: Long,      // the device's original timestamp (epoch ms or device units)
    val source: Source,
    val quality: Quality,
    val ingestedAt: String,     // ISO-8601 UTC when the app stored it
    val appVersion: String,
    val sdkVersion: String,
)

/**
 * Deterministic record identity: SHA-256 over the stable coordinates of a datum. Re-syncing the
 * same device/metric/timestamp yields the same id, so `INSERT OR IGNORE` drops duplicates. A
 * [discriminator] distinguishes multiple records that legitimately share those coordinates
 * (e.g. the stages within one sleep session).
 */
object RecordId {
    fun of(deviceId: String, metric: Metric, deviceTsRaw: Long, discriminator: String = ""): String =
        hash(deviceId, metric.name, deviceTsRaw, discriminator)

    /** Deterministic id over arbitrary stable parts (e.g. device events that aren't a [Metric]). */
    fun hash(vararg parts: Any?): String {
        val input = parts.joinToString("|") { it?.toString() ?: "" }
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
