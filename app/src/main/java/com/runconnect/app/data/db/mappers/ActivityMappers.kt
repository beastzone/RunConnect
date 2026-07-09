package com.runconnect.app.data.db.mappers

import com.runconnect.app.data.db.entities.ActivityEntity
import com.runconnect.app.data.db.entities.ActivityHrSampleEntity
import com.runconnect.app.data.db.entities.RoutePointEntity
import com.runconnect.app.domain.model.Activity
import com.runconnect.app.domain.model.ActivityType
import com.runconnect.app.domain.model.DataSource
import com.runconnect.app.domain.model.HeartRateSample
import com.runconnect.app.domain.model.RoutePoint
import java.time.Instant
import java.time.ZoneOffset

fun Activity.toEntity(syncedAtEpoch: Long): ActivityEntity = ActivityEntity(
    id = id,
    type = type.name,
    title = title,
    startTimeEpoch = startTime.epochSecond,
    endTimeEpoch = endTime.epochSecond,
    durationSeconds = durationSeconds,
    distanceMeters = distanceMeters,
    elevationGainMeters = elevationGainMeters,
    averageHeartRate = averageHeartRate,
    maxHeartRate = maxHeartRate,
    averageSpeedMps = averageSpeedMps,
    calories = calories,
    laps = laps,
    dataOriginPackage = dataOriginPackage,
    garminActivityId = garminActivityId,
    completenessScore = completenessScore,
    hasDuplicate = hasDuplicate,
    startZoneOffsetId = startZoneOffset?.id,
    syncedAtEpoch = syncedAtEpoch,
)

fun ActivityEntity.toDomain(
    hrSamples: List<HeartRateSample> = emptyList(),
    routePoints: List<RoutePoint> = emptyList(),
): Activity = Activity(
    id = id,
    type = runCatching { ActivityType.valueOf(type) }.getOrDefault(ActivityType.OTHER),
    title = title,
    startTime = Instant.ofEpochSecond(startTimeEpoch),
    endTime = Instant.ofEpochSecond(endTimeEpoch),
    durationSeconds = durationSeconds,
    distanceMeters = distanceMeters,
    elevationGainMeters = elevationGainMeters,
    averageHeartRate = averageHeartRate,
    maxHeartRate = maxHeartRate,
    averageSpeedMps = averageSpeedMps,
    calories = calories,
    laps = laps,
    route = routePoints,
    heartRateSamples = hrSamples,
    source = DataSource.HEALTH_CONNECT,
    garminActivityId = garminActivityId,
    dataOriginPackage = dataOriginPackage,
    completenessScore = completenessScore,
    hasDuplicate = hasDuplicate,
    startZoneOffset = startZoneOffsetId?.let { runCatching { ZoneOffset.of(it) }.getOrNull() },
)

fun HeartRateSample.toEntity(activityId: String): ActivityHrSampleEntity =
    ActivityHrSampleEntity(
        activityId = activityId,
        timestampEpoch = timestamp.epochSecond,
        bpm = bpm,
    )

fun ActivityHrSampleEntity.toDomain(): HeartRateSample =
    HeartRateSample(
        timestamp = Instant.ofEpochSecond(timestampEpoch),
        bpm = bpm,
    )

fun RoutePoint.toEntity(activityId: String): RoutePointEntity =
    RoutePointEntity(
        activityId = activityId,
        timestampEpoch = timestamp.epochSecond,
        latitude = latitude,
        longitude = longitude,
        altitudeMeters = altitudeMeters,
        heartRate = heartRate,
        speedMps = speedMps,
    )

fun RoutePointEntity.toDomain(): RoutePoint =
    RoutePoint(
        latitude = latitude,
        longitude = longitude,
        altitudeMeters = altitudeMeters,
        timestamp = Instant.ofEpochSecond(timestampEpoch),
        heartRate = heartRate,
        speedMps = speedMps,
    )
