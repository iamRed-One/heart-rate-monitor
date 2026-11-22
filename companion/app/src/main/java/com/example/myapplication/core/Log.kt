package com.example.myapplication.core

import android.util.Log as AndroidLog

/**
 * Minimal logging facade for Phase 1. Single tag so `adb logcat -s HB:*` captures everything;
 * wraps Android's Log so call sites don't import it directly (and so we can later tee to a file).
 */
object Log {
    private const val TAG = "HB"

    fun d(area: String, msg: String) = AndroidLog.d(TAG, "[$area] $msg").let {}
    fun i(area: String, msg: String) = AndroidLog.i(TAG, "[$area] $msg").let {}
    fun w(area: String, msg: String, t: Throwable? = null) = AndroidLog.w(TAG, "[$area] $msg", t).let {}
    fun e(area: String, msg: String, t: Throwable? = null) = AndroidLog.e(TAG, "[$area] $msg", t).let {}
}
