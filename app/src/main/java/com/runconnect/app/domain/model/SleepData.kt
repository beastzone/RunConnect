package com.runconnect.app.domain.model

import java.time.Instant
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

data class SleepSession(
    val id: String,
    val startTime: Instant,
    val endTime: Instant,
    val stages: List<SleepStage>,
    val source: DataSource = DataSource.HEALTH_CONNECT,
    val dataOriginPackage: String = "",
    val startZoneOffset: java.time.ZoneOffset? = null,
    val heartRateSamples: List<Pair<Instant, Int>> = emptyList(),
    val hrvSamples: List<Pair<Instant, Double>> = emptyList(),
    val respirationSamples: List<Pair<Instant, Double>> = emptyList(),
    val spo2Samples: List<Pair<Instant, Double>> = emptyList(),
) {
    val totalDurationMinutes: Long
        get() = (endTime.epochSecond - startTime.epochSecond) / 60

    val deepSleepMinutes: Long
        get() = stages.filter { it.type == SleepStageType.DEEP }
            .sumOf { (it.endTime.epochSecond - it.startTime.epochSecond) / 60 }

    val lightSleepMinutes: Long
        get() = stages.filter { it.type == SleepStageType.LIGHT }
            .sumOf { (it.endTime.epochSecond - it.startTime.epochSecond) / 60 }

    val remSleepMinutes: Long
        get() = stages.filter { it.type == SleepStageType.REM }
            .sumOf { (it.endTime.epochSecond - it.startTime.epochSecond) / 60 }

    val awakeMinutes: Long
        get() = stages.filter { it.type == SleepStageType.AWAKE || it.type == SleepStageType.OUT_OF_BED }
            .sumOf { (it.endTime.epochSecond - it.startTime.epochSecond) / 60 }

    val sleepEfficiencyPercent: Int
        get() {
            val asleepMinutes = deepSleepMinutes + lightSleepMinutes + remSleepMinutes
            return if (totalDurationMinutes > 0)
                ((asleepMinutes.toDouble() / totalDurationMinutes) * 100).toInt()
            else 0
        }

    val unspecifiedSleepMinutes: Long
        get() = stages.filter { it.type == SleepStageType.SLEEPING_UNSPECIFIED }
            .sumOf { (it.endTime.epochSecond - it.startTime.epochSecond) / 60 }

    val totalSleepMinutes: Long
        get() = deepSleepMinutes + lightSleepMinutes + remSleepMinutes + unspecifiedSleepMinutes

    val sleepOnset: Instant?
        get() = stages.firstOrNull {
            it.type != SleepStageType.AWAKE &&
            it.type != SleepStageType.OUT_OF_BED &&
            it.type != SleepStageType.UNKNOWN
        }?.startTime

    val sleepLatencyMinutes: Long
        get() = sleepOnset?.let { (it.epochSecond - startTime.epochSecond) / 60 } ?: 0L

    val wasoMinutes: Long
        get() {
            val onset = sleepOnset ?: return 0L
            return stages.filter {
                (it.type == SleepStageType.AWAKE || it.type == SleepStageType.OUT_OF_BED) &&
                it.startTime >= onset
            }.sumOf { (it.endTime.epochSecond - it.startTime.epochSecond) / 60 }
        }

    val awakeningCount: Int
        get() {
            val onset = sleepOnset ?: return 0
            return stages.count {
                (it.type == SleepStageType.AWAKE || it.type == SleepStageType.OUT_OF_BED) &&
                it.startTime >= onset
            }
        }

    val midpointTime: Instant?
        get() = sleepOnset?.plusSeconds((totalSleepMinutes / 2) * 60)

    val isNap: Boolean
        get() {
            if (totalDurationMinutes >= 180) return false
            val hour = startTime.atZone(ZoneId.systemDefault()).hour
            return hour in 8..19
        }

    val sleepScore: Int
        get() {
            val eff = sleepEfficiencyPercent
            val deepPct = if (totalDurationMinutes > 0) (deepSleepMinutes * 100 / totalDurationMinutes).toInt() else 0
            val remPct = if (totalDurationMinutes > 0) (remSleepMinutes * 100 / totalDurationMinutes).toInt() else 0
            val latencyBonus = if (sleepLatencyMinutes in 5..20) 10 else 0
            return (eff * 0.4 + deepPct * 0.3 + remPct * 0.2 + latencyBonus).toInt().coerceIn(0, 100)
        }

    val avgHeartRate: Int?
        get() = heartRateSamples.takeIf { it.isNotEmpty() }?.map { it.second }?.average()?.toInt()

    val lowestHeartRate: Int?
        get() = heartRateSamples.minOfOrNull { it.second }

    val timeOfLowestHr: Instant?
        get() = heartRateSamples.minByOrNull { it.second }?.first

    val avgHrv: Double?
        get() = hrvSamples.takeIf { it.isNotEmpty() }?.map { it.second }?.average()

    val avgRespiration: Double?
        get() = respirationSamples.takeIf { it.isNotEmpty() }?.map { it.second }?.average()

    val avgSpo2: Double?
        get() = spo2Samples.takeIf { it.isNotEmpty() }?.map { it.second }?.average()

    val minSpo2: Double?
        get() = spo2Samples.minOfOrNull { it.second }

    val timeBelowSpo2Threshold: Long
        get() = spo2Samples.zipWithNext().sumOf { (a, b) ->
            val mins = (b.first.epochSecond - a.first.epochSecond) / 60
            if (a.second < 95.0) mins else 0L
        }
}

data class SleepStage(
    val startTime: Instant,
    val endTime: Instant,
    val type: SleepStageType,
)

enum class SleepStageType {
    DEEP, LIGHT, REM, AWAKE, SLEEPING_UNSPECIFIED, OUT_OF_BED, UNKNOWN;

    companion object {
        fun fromHealthConnect(stage: Int): SleepStageType = when (stage) {
            1 -> AWAKE                // STAGE_TYPE_AWAKE
            2 -> SLEEPING_UNSPECIFIED // STAGE_TYPE_SLEEPING (no sub-stage)
            3 -> OUT_OF_BED          // STAGE_TYPE_OUT_OF_BED
            4 -> LIGHT               // STAGE_TYPE_SLEEPING_LIGHT
            5 -> DEEP                // STAGE_TYPE_SLEEPING_DEEP
            6 -> REM                 // STAGE_TYPE_SLEEPING_REM
            else -> UNKNOWN
        }
    }
}

enum class SleepConfidence { HIGH, MEDIUM, LOW }

data class SleepScoreResult(
    val score: Int,
    val rating: String,
    val durationScore: Int?,
    val durationWeight: Double,
    val efficiencyScore: Int?,
    val efficiencyWeight: Double,
    val continuityScore: Int?,
    val continuityWeight: Double,
    val consistencyScore: Int?,
    val consistencyWeight: Double,
    val stageScore: Int?,
    val stageWeight: Double,
    val recoveryScore: Int?,
    val recoveryWeight: Double,
    val confidence: SleepConfidence,
    val modelVersion: Int,
)

data class SleepAnnotation(
    val latencyCorrectionMinutes: Int = 0,
    val tags: List<String> = emptyList(),
    val notes: String = "",
    val roomTemp: String = "",
    val noiseLevel: String = "",
    val lightLevel: String = "",
    val isTravel: Boolean = false,
)

enum class SleepFactorTag(val label: String) {
    ALCOHOL("Alcohol"),
    CAFFEINE("Caffeine"),
    LATE_MEAL("Late Meal"),
    EVENING_WORKOUT("Evening Workout"),
    MEDICATION("Medication"),
    STRESS("Stress"),
    ILLNESS("Illness"),
    SCREEN_TIME("Screen Time"),
    TRAVEL("Travel"),
    MEDITATION("Meditation"),
    READING("Reading");

    val key: String get() = name.lowercase()
}

data class SleepDebtInfo(
    val lastNightDebt: Long,
    val sevenDayDebt: Long,
    val sleepNeed: Long,
)

data class BedtimeStats(
    val avgMinutes: Int,
    val earliestMinutes: Int,
    val latestMinutes: Int,
    val stdDevMinutes: Double,
    val weekdayAvgMinutes: Int,
    val weekendAvgMinutes: Int,
)

data class WakeTimeStats(
    val avgMinutes: Int,
    val earliestMinutes: Int,
    val latestMinutes: Int,
    val stdDevMinutes: Double,
    val weekdayAvgMinutes: Int,
    val weekendAvgMinutes: Int,
)

data class SleepRecommendation(
    val title: String,
    val body: String,
    val priority: Int = 0,
)

data class SleepCorrelation(
    val tag: String,
    val tagLabel: String,
    val sampleSize: Int,
    val avgDurationWith: Long,
    val avgDurationWithout: Long,
    val avgScoreWith: Int,
    val avgScoreWithout: Int,
)

data class WeeklySleepReport(
    val weekStartDate: java.time.LocalDate,
    val avgDurationMinutes: Long,
    val avgScore: Int,
    val avgDebt: Long,
    val consistencyMinutes: Double,
    val bestNightId: String?,
    val worstNightId: String?,
    val nightCount: Int,
)

data class MonthlySleepReport(
    val month: YearMonth,
    val avgDurationMinutes: Long,
    val avgScore: Int,
    val weekdayAvgMinutes: Long,
    val weekendAvgMinutes: Long,
    val longestSleepMinutes: Long,
    val shortestSleepMinutes: Long,
)

data class SleepPrediction(
    val predictedDurationMinutes: Long,
    val predictedScore: Int,
    val recommendedBedtime: LocalTime?,
    val debtProjection: Long,
)
