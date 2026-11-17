package com.example.myapplication.core

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Unambiguous timestamps: ISO-8601 UTC plus the local offset, per the Phase 1 schema. */
object Time {
    private val isoUtc = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }

    @Synchronized fun nowUtcIso(): String = isoUtc.format(Date())

    @Synchronized fun utcIso(epochMs: Long): String = isoUtc.format(Date(epochMs))

    /** Local offset from UTC, in minutes (e.g. +60 for UTC+1). */
    fun tzOffsetMin(): Int = TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60_000

    private val localHm = SimpleDateFormat("HH:mm", Locale.US)
    private val localDate = SimpleDateFormat("MMM d, HH:mm", Locale.US)

    /** Local time-of-day, e.g. "07:30" (for detail-screen rows). */
    fun localHm(epochMs: Long): String = localHm.format(Date(epochMs))

    /** Local date + time, e.g. "Oct 22, 07:30". */
    fun localDateTime(epochMs: Long): String = localDate.format(Date(epochMs))
}
