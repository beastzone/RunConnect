package com.runconnect.app.data.healthconnect

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.PowerRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.SleepStageRecord
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.runconnect.app.domain.model.Activity
import com.runconnect.app.domain.model.ActivityType
import com.runconnect.app.domain.model.DataSource
import com.runconnect.app.domain.model.HeartRateSample
import com.runconnect.app.domain.model.LapData
import com.runconnect.app.domain.model.RoutePoint
import com.runconnect.app.domain.model.SleepSession
import com.runconnect.app.domain.model.SleepStage
import com.runconnect.app.domain.model.SleepStageType
import com.runconnect.app.domain.model.SpeedSample
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HealthConnectManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val client: HealthConnectClient? by lazy {
        runCatching { HealthConnectClient.getOrCreate(context) }.getOrNull()
    }

    val isAvailable: Boolean
        get() = HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    val requiredPermissions = setOf(
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(SleepStageRecord::class),
        HealthPermission.getReadPermission(SpeedRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(ElevationGainedRecord::class),
        HealthPermission.getReadPermission(PowerRecord::class),
    )

    suspend fun checkPermissions(): Set<String> {
        val c = client ?: return emptySet()
        return c.permissionController.getGrantedPermissions()
    }

    suspend fun hasAllPermissions(): Boolean {
        val granted = checkPermissions()
        return requiredPermissions.all { it in granted }
    }

    suspend fun readActivities(
        startTime: Instant = Instant.now().minus(90, ChronoUnit.DAYS),
        endTime: Instant = Instant.now(),
    ): List<Activity> {
        val c = client ?: return emptyList()
        val sessions = c.readRecords(
            ReadRecordsRequest(
                recordType = ExerciseSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime),
                ascendingOrder = false,
            )
        ).records

        return sessions.map { session ->
            val heartRateSamples = readHeartRateSamples(c, session.startTime, session.endTime)
            val speedSamples = readSpeedSamples(c, session.startTime, session.endTime)
            val distance = readTotalDistance(c, session.startTime, session.endTime)
            val calories = readCalories(c, session.startTime, session.endTime)
            val elevation = readElevationGain(c, session.startTime, session.endTime)
            val route = session.route?.locations?.map { loc ->
                RoutePoint(
                    latitude = loc.latitude,
                    longitude = loc.longitude,
                    altitudeMeters = loc.altitude?.inMeters,
                    timestamp = loc.time,
                )
            } ?: emptyList()
            val laps = session.segments.mapIndexed { idx, seg ->
                LapData(
                    lapNumber = idx + 1,
                    startTime = seg.startTime,
                    endTime = seg.endTime,
                    distanceMeters = 0.0,
                    durationSeconds = seg.endTime.epochSecond - seg.startTime.epochSecond,
                    averageHeartRate = heartRateSamples
                        .filter { it.timestamp >= seg.startTime && it.timestamp <= seg.endTime }
                        .map { it.bpm }.average().takeIf { !it.isNaN() }?.toInt(),
                )
            }

            val durationS = session.endTime.epochSecond - session.startTime.epochSecond
            val avgSpeed = if (distance > 0 && durationS > 0) distance / durationS else null
            val avgHr = heartRateSamples.map { it.bpm }.average().takeIf { !it.isNaN() }?.toInt()
            val maxHr = heartRateSamples.maxOfOrNull { it.bpm }?.toInt()

            Activity(
                id = session.metadata.id,
                type = ActivityType.fromHealthConnectType(session.exerciseType),
                title = session.title ?: ActivityType.fromHealthConnectType(session.exerciseType).label,
                startTime = session.startTime,
                endTime = session.endTime,
                durationSeconds = durationS,
                distanceMeters = distance,
                elevationGainMeters = elevation,
                averageHeartRate = avgHr,
                maxHeartRate = maxHr,
                averageSpeedMps = avgSpeed,
                calories = calories?.toInt(),
                laps = laps,
                route = route,
                heartRateSamples = heartRateSamples,
                speedSamples = speedSamples,
                source = DataSource.HEALTH_CONNECT,
            )
        }
    }

    suspend fun readActivityById(id: String): Activity? =
        readActivities().firstOrNull { it.id == id }

    suspend fun readSleepSessions(
        startTime: Instant = Instant.now().minus(30, ChronoUnit.DAYS),
        endTime: Instant = Instant.now(),
    ): List<SleepSession> {
        val c = client ?: return emptyList()
        val sessions = c.readRecords(
            ReadRecordsRequest(
                recordType = SleepSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime),
                ascendingOrder = false,
            )
        ).records

        return sessions.map { session ->
            val stages = session.stages.map { stage ->
                SleepStage(
                    startTime = stage.startTime,
                    endTime = stage.endTime,
                    type = SleepStageType.fromHealthConnect(stage.stage),
                )
            }
            SleepSession(
                id = session.metadata.id,
                startTime = session.startTime,
                endTime = session.endTime,
                stages = stages,
                source = DataSource.HEALTH_CONNECT,
            )
        }
    }

    private suspend fun readHeartRateSamples(
        c: HealthConnectClient,
        start: Instant,
        end: Instant,
    ): List<HeartRateSample> =
        c.readRecords(
            ReadRecordsRequest(
                recordType = HeartRateRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end),
            )
        ).records.flatMap { record ->
            record.samples.map { HeartRateSample(it.time, it.beatsPerMinute) }
        }

    private suspend fun readSpeedSamples(
        c: HealthConnectClient,
        start: Instant,
        end: Instant,
    ): List<SpeedSample> =
        c.readRecords(
            ReadRecordsRequest(
                recordType = SpeedRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end),
            )
        ).records.flatMap { record ->
            record.samples.map { SpeedSample(it.time, it.speed.inMetersPerSecond) }
        }

    private suspend fun readTotalDistance(
        c: HealthConnectClient,
        start: Instant,
        end: Instant,
    ): Double =
        c.readRecords(
            ReadRecordsRequest(
                recordType = DistanceRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end),
            )
        ).records.sumOf { it.distance.inMeters }

    private suspend fun readCalories(
        c: HealthConnectClient,
        start: Instant,
        end: Instant,
    ): Double? =
        c.readRecords(
            ReadRecordsRequest(
                recordType = ActiveCaloriesBurnedRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end),
            )
        ).records.sumOf { it.energy.inKilocalories }.takeIf { it > 0 }

    private suspend fun readElevationGain(
        c: HealthConnectClient,
        start: Instant,
        end: Instant,
    ): Double =
        c.readRecords(
            ReadRecordsRequest(
                recordType = ElevationGainedRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end),
            )
        ).records.sumOf { it.elevation.inMeters }
}
