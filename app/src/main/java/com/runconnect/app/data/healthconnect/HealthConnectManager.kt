package com.runconnect.app.data.healthconnect

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.PowerRecord
import androidx.health.connect.client.records.RespirationRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.changes.UpsertionChange
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.records.ExerciseRouteResult
import androidx.health.connect.client.time.TimeRangeFilter
import com.runconnect.app.domain.model.Activity
import com.runconnect.app.domain.model.ActivityType
import com.runconnect.app.domain.model.BodyMetricsSample
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
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

data class ChangeSummary(val hasExerciseChanges: Boolean, val newToken: String)

data class PermissionInfo(val permission: String, val displayName: String, val usedFor: String)

data class RouteResult(
    val points: List<RoutePoint> = emptyList(),
    val consentRequired: Boolean = false,
)

@Singleton
class HealthConnectManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val client: HealthConnectClient? by lazy {
        runCatching { HealthConnectClient.getOrCreate(context) }.getOrNull()
    }

    val sdkStatus: Int
        get() = HealthConnectClient.getSdkStatus(context)

    val isAvailable: Boolean
        get() = sdkStatus == HealthConnectClient.SDK_AVAILABLE

    val permissionInfoList = listOf(
        PermissionInfo(HealthPermission.getReadPermission(ExerciseSessionRecord::class), "Exercise Sessions", "Activity list: runs, hikes, walks, cycles"),
        PermissionInfo("android.permission.health.READ_EXERCISE_ROUTE", "GPS Routes", "3D route map on activity detail"),
        PermissionInfo(HealthPermission.getReadPermission(HeartRateRecord::class), "Heart Rate", "HR charts and zone analysis"),
        PermissionInfo(HealthPermission.getReadPermission(SleepSessionRecord::class), "Sleep", "Sleep analytics and recovery score"),
        PermissionInfo(HealthPermission.getReadPermission(SpeedRecord::class), "Speed / Pace", "Pace chart on activity detail"),
        PermissionInfo(HealthPermission.getReadPermission(DistanceRecord::class), "Distance", "Activity distance and pace calculation"),
        PermissionInfo(HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class), "Active Calories", "Calorie tracking on dashboard and activities"),
        PermissionInfo(HealthPermission.getReadPermission(ElevationGainedRecord::class), "Elevation", "Elevation profile chart"),
        PermissionInfo(HealthPermission.getReadPermission(PowerRecord::class), "Power", "Cycling power data"),
        PermissionInfo(HealthPermission.getReadPermission(StepsRecord::class), "Steps", "Daily step count on dashboard"),
        PermissionInfo(HealthPermission.getReadPermission(RestingHeartRateRecord::class), "Resting Heart Rate", "Resting HR trend and recovery score"),
        PermissionInfo(HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class), "HRV", "HRV trend and recovery insights"),
        PermissionInfo(HealthPermission.getReadPermission(WeightRecord::class), "Weight", "Body weight trend from Withings"),
        PermissionInfo(HealthPermission.getReadPermission(BodyFatRecord::class), "Body Fat", "Body fat % trend from Withings"),
        PermissionInfo(HealthPermission.getReadPermission(OxygenSaturationRecord::class), "Oxygen Saturation (SpO2)", "Overnight SpO2 in sleep analytics"),
        PermissionInfo(HealthPermission.getReadPermission(RespirationRateRecord::class), "Respiratory Rate", "Overnight breathing rate in sleep analytics"),
    )

    val requiredPermissions = permissionInfoList.map { it.permission }.toSet()

    suspend fun checkPermissions(): Set<String> {
        val c = client ?: return emptySet()
        return c.permissionController.getGrantedPermissions()
    }

    suspend fun hasAllPermissions(): Boolean {
        val granted = checkPermissions()
        return requiredPermissions.all { it in granted }
    }

    // Reads all activities for the range using 6 bulk API calls instead of 5*N+1.
    // Previously each activity triggered 5 separate HC reads (HR, speed, distance,
    // calories, elevation), blowing through HC's rate limit for any non-trivial history.
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

        if (sessions.isEmpty()) return emptyList()

        // Bulk-read all supporting data for the full range — one call each
        val allHr = c.readRecords(
            ReadRecordsRequest(
                recordType = HeartRateRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime),
            )
        ).records

        val allSpeed = c.readRecords(
            ReadRecordsRequest(
                recordType = SpeedRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime),
            )
        ).records

        val allDistance = c.readRecords(
            ReadRecordsRequest(
                recordType = DistanceRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime),
            )
        ).records

        val allCalories = c.readRecords(
            ReadRecordsRequest(
                recordType = ActiveCaloriesBurnedRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime),
            )
        ).records

        val allElevation = c.readRecords(
            ReadRecordsRequest(
                recordType = ElevationGainedRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime),
            )
        ).records

        return sessions.map { session ->
            val sStart = session.startTime
            val sEnd = session.endTime

            val heartRateSamples = allHr
                .filter { it.startTime >= sStart && it.endTime <= sEnd }
                .flatMap { r -> r.samples.map { HeartRateSample(it.time, it.beatsPerMinute) } }

            val speedSamples = allSpeed
                .filter { it.startTime >= sStart && it.endTime <= sEnd }
                .flatMap { r -> r.samples.map { SpeedSample(it.time, it.speed.inMetersPerSecond) } }

            val distance = allDistance
                .filter { it.startTime >= sStart && it.endTime <= sEnd }
                .sumOf { it.distance.inMeters }

            val calories = allCalories
                .filter { it.startTime >= sStart && it.endTime <= sEnd }
                .sumOf { it.energy.inKilocalories }.takeIf { it > 0 }

            val elevation = allElevation
                .filter { it.startTime >= sStart && it.endTime <= sEnd }
                .sumOf { it.elevation.inMeters }

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

            val durationS = sEnd.epochSecond - sStart.epochSecond
            val avgSpeed = if (distance > 0 && durationS > 0) distance / durationS else null
            val avgHr = heartRateSamples.map { it.bpm }.average().takeIf { !it.isNaN() }?.toInt()
            val maxHr = heartRateSamples.maxOfOrNull { it.bpm }?.toInt()
            val completeness = run {
                var s = 0
                if (heartRateSamples.isNotEmpty()) s += 30
                if (distance > 0) s += 25
                if (calories != null) s += 15
                if (elevation > 0) s += 15
                if (laps.isNotEmpty()) s += 15
                s
            }

            Activity(
                id = session.metadata.id,
                type = ActivityType.fromHealthConnectType(session.exerciseType),
                title = session.title ?: ActivityType.fromHealthConnectType(session.exerciseType).label,
                startTime = sStart,
                endTime = sEnd,
                durationSeconds = durationS,
                distanceMeters = distance,
                elevationGainMeters = elevation,
                averageHeartRate = avgHr,
                maxHeartRate = maxHr,
                averageSpeedMps = avgSpeed,
                calories = calories?.toInt(),
                laps = laps,
                route = emptyList(),
                heartRateSamples = heartRateSamples,
                speedSamples = speedSamples,
                source = DataSource.HEALTH_CONNECT,
                dataOriginPackage = session.metadata.dataOrigin.packageName,
                completenessScore = completeness,
                startZoneOffset = session.startZoneOffset,
            )
        }
    }

    // Reads all history in 90-day chunks, calling onProgress after each chunk.
    suspend fun readActivitiesChunked(
        totalStart: Instant,
        totalEnd: Instant = Instant.now(),
        onProgress: (loaded: Int, label: String) -> Unit,
    ): List<Activity> {
        val all = mutableListOf<Activity>()
        var chunkEnd = totalEnd
        val chunkDays = 90L
        while (chunkEnd.isAfter(totalStart)) {
            val chunkStart = maxOf(chunkEnd.minus(chunkDays, ChronoUnit.DAYS), totalStart)
            val chunk = readActivities(chunkStart, chunkEnd)
            all.addAll(chunk)
            val ldt = chunkStart.atZone(ZoneId.systemDefault())
            val monthName = ldt.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)
            onProgress(all.size, "$monthName ${ldt.year}")
            if (chunkStart == totalStart) break
            chunkEnd = chunkStart
        }
        return all
    }

    suspend fun readActivityById(id: String): Activity? =
        readActivities().firstOrNull { it.id == id }

    suspend fun readExerciseRoute(sessionId: String): RouteResult {
        val c = client ?: return RouteResult()
        return runCatching {
            val record = c.readRecord(ExerciseSessionRecord::class, sessionId).record
            when (val routeResult = record.exerciseRouteResult) {
                is ExerciseRouteResult.Data -> RouteResult(
                    points = routeResult.exerciseRoute.route.map { loc ->
                        RoutePoint(
                            latitude = loc.latitude,
                            longitude = loc.longitude,
                            altitudeMeters = loc.altitude?.inMeters,
                            timestamp = loc.time,
                        )
                    }
                )
                is ExerciseRouteResult.ConsentRequired -> RouteResult(consentRequired = true)
                else -> RouteResult()
            }
        }.getOrDefault(RouteResult())
    }

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

        val baseSessions = sessions.map { session ->
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
                dataOriginPackage = session.metadata.dataOrigin.packageName,
                startZoneOffset = session.startZoneOffset,
            )
        }

        val hrRecords = runCatching {
            c.readRecords(ReadRecordsRequest(HeartRateRecord::class, TimeRangeFilter.between(startTime, endTime))).records
        }.getOrDefault(emptyList())

        val hrvRecords = runCatching {
            c.readRecords(ReadRecordsRequest(HeartRateVariabilityRmssdRecord::class, TimeRangeFilter.between(startTime, endTime))).records
        }.getOrDefault(emptyList())

        val spo2Records = runCatching {
            c.readRecords(ReadRecordsRequest(OxygenSaturationRecord::class, TimeRangeFilter.between(startTime, endTime))).records
        }.getOrDefault(emptyList())

        val respRecords = runCatching {
            c.readRecords(ReadRecordsRequest(RespirationRateRecord::class, TimeRangeFilter.between(startTime, endTime))).records
        }.getOrDefault(emptyList())

        return baseSessions.map { session ->
            session.copy(
                heartRateSamples = hrRecords.flatMap { r ->
                    r.samples.filter { it.time >= session.startTime && it.time <= session.endTime }
                        .map { it.time to it.beatsPerMinute.toInt() }
                },
                hrvSamples = hrvRecords.filter { it.time >= session.startTime && it.time <= session.endTime }
                    .map { it.time to it.heartRateVariabilityMillis },
                spo2Samples = spo2Records.filter { it.time >= session.startTime && it.time <= session.endTime }
                    .map { it.time to it.percentage.value },
                respirationSamples = respRecords.filter { it.time >= session.startTime && it.time <= session.endTime }
                    .map { it.time to it.rate },
            )
        }
    }

    suspend fun readRestingHeartRateHistory(days: Int = 30): List<Pair<Instant, Int>> {
        val c = client ?: return emptyList()
        val start = Instant.now().minus(days.toLong(), ChronoUnit.DAYS)
        return c.readRecords(
            ReadRecordsRequest(
                recordType = RestingHeartRateRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, Instant.now()),
                ascendingOrder = false,
            )
        ).records.map { it.time to it.beatsPerMinute.toInt() }
    }

    suspend fun readHrvHistory(days: Int = 30): List<Pair<Instant, Double>> {
        val c = client ?: return emptyList()
        val start = Instant.now().minus(days.toLong(), ChronoUnit.DAYS)
        return c.readRecords(
            ReadRecordsRequest(
                recordType = HeartRateVariabilityRmssdRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, Instant.now()),
                ascendingOrder = false,
            )
        ).records.map { it.time to it.heartRateVariabilityMillis }
    }

    suspend fun readStepsForRange(start: Instant, end: Instant): Long {
        val c = client ?: return 0L
        return c.readRecords(
            ReadRecordsRequest(
                recordType = StepsRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end),
            )
        ).records.sumOf { it.count }
    }

    suspend fun readActiveCaloriesForRange(start: Instant, end: Instant): Double {
        val c = client ?: return 0.0
        return c.readRecords(
            ReadRecordsRequest(
                recordType = ActiveCaloriesBurnedRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end),
            )
        ).records.sumOf { it.energy.inKilocalories }
    }

    suspend fun readWeightHistory(days: Int = 90): List<BodyMetricsSample> {
        val c = client ?: return emptyList()
        val start = Instant.now().minus(days.toLong(), ChronoUnit.DAYS)
        return c.readRecords(
            ReadRecordsRequest(
                recordType = WeightRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, Instant.now()),
                ascendingOrder = false,
            )
        ).records.map {
            BodyMetricsSample(timestamp = it.time, weightKg = it.weight.inKilograms, bodyFatPercent = null)
        }
    }

    // Returns a token scoped to ExerciseSessionRecord changes. Null if HC unavailable.
    suspend fun getChangesToken(): String? {
        val c = client ?: return null
        return runCatching {
            c.getChangesToken(ChangesTokenRequest(recordTypes = setOf(ExerciseSessionRecord::class)))
        }.getOrNull()
    }

    // Drains the change log since `token`. Null means the token expired — caller should do a full fetch.
    suspend fun processChanges(token: String): ChangeSummary? {
        val c = client ?: return null
        return runCatching {
            var currentToken = token
            var hasChanges = false
            var hasMore = true
            while (hasMore) {
                val response = c.getChanges(currentToken)
                if (!hasChanges) hasChanges = response.changes.any { it is UpsertionChange }
                currentToken = response.nextChangesToken
                hasMore = response.hasMore
            }
            ChangeSummary(hasExerciseChanges = hasChanges, newToken = currentToken)
        }.getOrNull()
    }

    suspend fun readBodyFatHistory(days: Int = 90): List<BodyMetricsSample> {
        val c = client ?: return emptyList()
        val start = Instant.now().minus(days.toLong(), ChronoUnit.DAYS)
        return c.readRecords(
            ReadRecordsRequest(
                recordType = BodyFatRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, Instant.now()),
                ascendingOrder = false,
            )
        ).records.map {
            BodyMetricsSample(timestamp = it.time, weightKg = null, bodyFatPercent = it.percentage.value)
        }
    }
}
