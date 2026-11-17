package com.example.myapplication.core

/**
 * A parsed device reading, independent of Room and provenance. History mappers produce these; the
 * sync engine stamps [Provenance] + a deterministic record id and writes the matching entity.
 * [deviceTsMs] is the record's own device timestamp (epoch ms) — the de-dup coordinate.
 */
sealed interface Reading {
    val metric: Metric
    val deviceTsMs: Long
    val quality: Quality

    /** A single scalar value (HR, SpO2, temperature, HRV, respiration, glucose). */
    data class Scalar(
        override val metric: Metric,
        override val deviceTsMs: Long,
        val value: Double,
        val unit: String,
        override val quality: Quality = Quality.OK,
    ) : Reading

    data class BloodPressure(
        override val deviceTsMs: Long,
        val sbp: Int,
        val dbp: Int,
        override val quality: Quality = Quality.OK,
    ) : Reading {
        override val metric = Metric.BLOOD_PRESSURE
    }

    data class SleepSession(
        override val deviceTsMs: Long,
        val startMs: Long,
        val endMs: Long,
        val totalMin: Int,
        val deepMin: Int,
        val lightMin: Int,
        val remMin: Int,
        val awakeMin: Int,
        val stages: List<Stage> = emptyList(),
        override val quality: Quality = Quality.OK,
    ) : Reading {
        override val metric = Metric.SLEEP
        data class Stage(val stage: String, val startMs: Long, val endMs: Long, val durationS: Int)
    }

    data class SportSession(
        override val deviceTsMs: Long,
        val startMs: Long,
        val endMs: Long,
        val steps: Int,
        val distanceM: Double,
        val calorieKcal: Double,
        override val quality: Quality = Quality.OK,
    ) : Reading {
        override val metric = Metric.SPORT
    }

    /** Lab-style blood panel the ring estimates (comprehensive measurement). */
    data class BloodComponents(
        override val deviceTsMs: Long,
        val cholesterolMmolL: Double?,
        val uricAcidUmolL: Double?,
        override val quality: Quality = Quality.ESTIMATED,
    ) : Reading {
        override val metric = Metric.BLOOD_COMPONENTS
    }

    /** Body composition (BIA) — body fat % headline; quality ESTIMATED (wearable BIA). */
    data class BodyComposition(
        override val deviceTsMs: Long,
        val bodyFatPct: Double,
        override val quality: Quality = Quality.ESTIMATED,
    ) : Reading {
        override val metric = Metric.BODY_COMPOSITION
    }
}
