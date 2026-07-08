package com.runconnect.app.domain.model

import java.time.Instant
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

fun computeHrZone(bpm: Int, maxHr: Int): HrZone {
    val pct = bpm.toFloat() / maxHr
    return when {
        pct < 0.60f -> HrZone.ZONE_1
        pct < 0.70f -> HrZone.ZONE_2
        pct < 0.80f -> HrZone.ZONE_3
        pct < 0.90f -> HrZone.ZONE_4
        else -> HrZone.ZONE_5
    }
}
