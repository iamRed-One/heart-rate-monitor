package com.example.myapplication.core

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Unambiguous timestamps: ISO-8601 UTC for captured heart-rate samples. */
object Time {
    private val isoUtc = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }


    @Synchronized fun utcIso(epochMs: Long): String = isoUtc.format(Date(epochMs))

}
