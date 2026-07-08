package com.runconnect.app.domain.model

import java.time.Instant

data class SleepSession(
    val id: String,
    val startTime: Instant,
    val endTime: Instant,
    val stages: List<SleepStage>,
    val source: DataSource = DataSource.HEALTH_CONNECT,
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
        get() = stages.filter { it.type == SleepStageType.AWAKE }
            .sumOf { (it.endTime.epochSecond - it.startTime.epochSecond) / 60 }

    val sleepEfficiencyPercent: Int
        get() {
            val asleepMinutes = deepSleepMinutes + lightSleepMinutes + remSleepMinutes
            return if (totalDurationMinutes > 0)
                ((asleepMinutes.toDouble() / totalDurationMinutes) * 100).toInt()
            else 0
        }
}

data class SleepStage(
    val startTime: Instant,
    val endTime: Instant,
    val type: SleepStageType,
)

enum class SleepStageType {
    DEEP, LIGHT, REM, AWAKE, UNKNOWN;

    companion object {
        fun fromHealthConnect(stage: Int): SleepStageType = when (stage) {
            4 -> DEEP     // STAGE_TYPE_SLEEPING_DEEP
            1 -> LIGHT    // STAGE_TYPE_SLEEPING_LIGHT
            5 -> REM      // STAGE_TYPE_SLEEPING_REM
            2 -> AWAKE    // STAGE_TYPE_AWAKE
            else -> UNKNOWN
        }
    }
}
