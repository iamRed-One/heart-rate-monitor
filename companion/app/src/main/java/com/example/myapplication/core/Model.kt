package com.example.myapplication.core

/** Vendor adapter types used by device connections and readings. */
enum class Metric {
    HEART_RATE, BLOOD_PRESSURE, SPO2, TEMPERATURE, HRV, RESPIRATORY_RATE,
    SLEEP, SPORT, ECG, GLUCOSE, BLOOD_COMPONENTS, BODY_COMPOSITION,
    PPG, PPI, ACCELEROMETER, STRESS,
}

enum class DeviceKind { RING, WATCH, UNKNOWN }

enum class Quality { OK, SUSPECT, UNWORN, ESTIMATED }
