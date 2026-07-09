package com.runconnect.app.domain.model

import java.time.LocalDate

data class DailyHeartRate(
    val date: LocalDate,
    val restingBpm: Int?,
    val minBpm: Int?,
    val maxBpm: Int?,
    val avgBpm: Int?,
)

data class HeartRateZoneSummary(
    val zone: HrZone,
    val totalMinutes: Long,
    val percentOfTotal: Float,
)

enum class HrZone(val label: String, val color: Long) {
    ZONE_1("Easy", 0xFF60A5FA),
    ZONE_2("Aerobic", 0xFF3DD9C5),
    ZONE_3("Tempo", 0xFFFFB347),
    ZONE_4("Threshold", 0xFFFF8C42),
    ZONE_5("Max", 0xFFFF6B6B);
}

enum class ZoneModel { MAX_HR, HRR }

fun computeHrZone(
    bpm: Int,
    maxHr: Int,
    model: ZoneModel = ZoneModel.MAX_HR,
    restingHr: Int = 60,
): HrZone = when (model) {
    ZoneModel.MAX_HR -> {
        val pct = bpm.toFloat() / maxHr
        when {
            pct < 0.60f -> HrZone.ZONE_1
            pct < 0.70f -> HrZone.ZONE_2
            pct < 0.80f -> HrZone.ZONE_3
            pct < 0.90f -> HrZone.ZONE_4
            else -> HrZone.ZONE_5
        }
    }
    ZoneModel.HRR -> {
        val reserve = (maxHr - restingHr).coerceAtLeast(1)
        val pct = (bpm - restingHr).toFloat() / reserve
        when {
            pct < 0.50f -> HrZone.ZONE_1
            pct < 0.60f -> HrZone.ZONE_2
            pct < 0.70f -> HrZone.ZONE_3
            pct < 0.85f -> HrZone.ZONE_4
            else -> HrZone.ZONE_5
        }
    }
}

// New domain model types for Feature 12 analytics
data class DailyZoneSummary(val date: LocalDate, val zones: List<HeartRateZoneSummary>)

data class RhrRollingAvgs(val d7: Int?, val d14: Int?, val d30: Int?, val d90: Int?)

data class ElevatedRhrAlert(
    val currentAvg: Int,
    val baseline: Int,
    val possibleCauses: List<String>,
)

data class LowRhrAlert(val currentAvg: Int, val baseline: Int)

data class WorkoutRecoveryPoint(
    val date: LocalDate,
    val drop1Min: Int?,
    val drop5Min: Int?,
)

data class HrByTypeStats(val avgHr: Int, val maxHr: Int, val activityCount: Int)
