package com.runconnect.app.domain.model

import java.time.Instant

data class Activity(
    val id: String,
    val type: ActivityType,
    val title: String,
    val startTime: Instant,
    val endTime: Instant,
    val durationSeconds: Long,
    val distanceMeters: Double,
    val elevationGainMeters: Double = 0.0,
    val averageHeartRate: Int? = null,
    val maxHeartRate: Int? = null,
    val averageSpeedMps: Double? = null,
    val calories: Int? = null,
    val laps: List<LapData> = emptyList(),
    val route: List<RoutePoint> = emptyList(),
    val heartRateSamples: List<HeartRateSample> = emptyList(),
    val speedSamples: List<SpeedSample> = emptyList(),
    val source: DataSource = DataSource.HEALTH_CONNECT,
    val garminActivityId: Long? = null,
)

enum class ActivityType(val label: String) {
    RUNNING("Run"),
    HIKING("Hike"),
    WALKING("Walk"),
    CYCLING("Cycle"),
    OTHER("Workout");

    companion object {
        fun fromHealthConnectType(type: Int): ActivityType = when (type) {
            56 -> RUNNING   // ExerciseSessionRecord.EXERCISE_TYPE_RUNNING
            37 -> HIKING    // ExerciseSessionRecord.EXERCISE_TYPE_HIKING
            79 -> WALKING   // ExerciseSessionRecord.EXERCISE_TYPE_WALKING
            8  -> CYCLING   // ExerciseSessionRecord.EXERCISE_TYPE_BIKING
            else -> OTHER
        }
    }
}

enum class DataSource { HEALTH_CONNECT, GARMIN_API, COMBINED }

data class RoutePoint(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double?,
    val timestamp: Instant,
    val heartRate: Int? = null,
    val speedMps: Double? = null,
)

data class HeartRateSample(
    val timestamp: Instant,
    val bpm: Long,
)

data class SpeedSample(
    val timestamp: Instant,
    val speedMps: Double,
) {
    val paceSecondsPerKm: Double get() = if (speedMps > 0) 1000.0 / speedMps else 0.0
    val paceSecondsPerMile: Double get() = if (speedMps > 0) 1609.344 / speedMps else 0.0
}

data class LapData(
    val lapNumber: Int,
    val startTime: Instant,
    val endTime: Instant,
    val distanceMeters: Double,
    val durationSeconds: Long,
    val averageHeartRate: Int? = null,
    val averageSpeedMps: Double? = null,
) {
    val paceSecondsPerKm: Double get() = if (averageSpeedMps != null && averageSpeedMps > 0) 1000.0 / averageSpeedMps else 0.0
}

// Extension helpers
val Activity.averagePaceSecondsPerKm: Double
    get() = if ((averageSpeedMps ?: 0.0) > 0) 1000.0 / averageSpeedMps!! else 0.0

val Activity.averagePaceSecondsPerMile: Double
    get() = if ((averageSpeedMps ?: 0.0) > 0) 1609.344 / averageSpeedMps!! else 0.0

val Activity.distanceKm: Double get() = distanceMeters / 1000.0
val Activity.distanceMiles: Double get() = distanceMeters / 1609.344
