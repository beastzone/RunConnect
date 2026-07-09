package com.runconnect.app.data.repository

import com.runconnect.app.data.db.dao.ActivityDao
import com.runconnect.app.data.db.dao.ActivityHrSampleDao
import com.runconnect.app.data.db.dao.RoutePointDao
import com.runconnect.app.data.db.dao.SyncStateDao
import com.runconnect.app.data.db.entities.SyncStateEntity
import com.runconnect.app.data.db.mappers.toDomain
import com.runconnect.app.data.db.mappers.toEntity
import com.runconnect.app.data.healthconnect.HealthConnectManager
import com.runconnect.app.data.healthconnect.RouteResult
import com.runconnect.app.data.preferences.AppPreferences
import com.runconnect.app.domain.model.Activity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

data class ImportProgress(
    val isRunning: Boolean = false,
    val activitiesLoaded: Int = 0,
    val currentPeriod: String = "",
)

@Singleton
class ActivityRepository @Inject constructor(
    private val healthConnectManager: HealthConnectManager,
    private val appPreferences: AppPreferences,
    private val activityDao: ActivityDao,
    private val activityHrSampleDao: ActivityHrSampleDao,
    private val routePointDao: RoutePointDao,
    private val syncStateDao: SyncStateDao,
) {
    // L1: process-lifetime hot cache (survives config changes, not app restarts)
    private val cache = mutableListOf<Activity>()
    private var cacheTime: Instant? = null

    private val _importProgress = MutableStateFlow(ImportProgress())
    val importProgress: StateFlow<ImportProgress> = _importProgress

    val cacheSize: Int get() = cache.size
    fun getCachedIds(): Set<String> = cache.map { it.id }.toSet()

    fun getActivities(
        daysBack: Int = 90,
        forceRefresh: Boolean = false,
    ): Flow<Result<List<Activity>>> = flow {
        val now = Instant.now()
        val startEpoch = now.minus(daysBack.toLong(), ChronoUnit.DAYS).epochSecond
        val cacheExpired = cacheTime?.let {
            now.isAfter(it.plus(5, ChronoUnit.MINUTES))
        } ?: true

        // L1 hit — return immediately
        if (!forceRefresh && !cacheExpired && cache.isNotEmpty()) {
            emit(Result.success(cache.toList()))
            return@flow
        }

        // Change-token optimisation: if nothing changed in HC, skip full fetch
        if (!forceRefresh && cache.isNotEmpty()) {
            val savedToken = loadChangesToken()
            if (savedToken != null) {
                val summary = healthConnectManager.processChanges(savedToken)
                if (summary != null && !summary.hasExerciseChanges) {
                    saveChangesToken(summary.newToken)
                    cacheTime = now
                    emit(Result.success(cache.toList()))
                    return@flow
                }
                if (summary != null) saveChangesToken(summary.newToken)
            }
        }

        // L2: Room — load if cache is cold (process restart or first launch)
        if (!forceRefresh && cache.isEmpty()) {
            val roomActivities = activityDao.getActivitiesSince(startEpoch)
            if (roomActivities.isNotEmpty()) {
                val syncState = syncStateDao.getForType(SYNC_TYPE)
                val roomFresh = syncState?.lastSyncEpoch?.let {
                    now.epochSecond - it < CACHE_TTL_SECONDS
                } ?: false
                if (roomFresh) {
                    val samplesByActivity = activityHrSampleDao
                        .getForActivities(roomActivities.map { it.id })
                        .groupBy { it.activityId }
                        .mapValues { (_, entities) -> entities.map { it.toDomain() } }
                    val domain = roomActivities.map { entity ->
                        entity.toDomain(hrSamples = samplesByActivity[entity.id] ?: emptyList())
                    }
                    cache.clear()
                    cache.addAll(domain)
                    cacheTime = now
                    emit(Result.success(domain))
                    return@flow
                }
            }
        }

        // HC fetch
        val fetchResult = runCatching {
            healthConnectManager.readActivities(Instant.ofEpochSecond(startEpoch), now)
        }
        if (fetchResult.isSuccess) {
            val activities = tagDuplicates(fetchResult.getOrThrow())
            cache.clear()
            cache.addAll(activities)
            cacheTime = now

            // Persist to Room
            upsertActivitiesToRoom(activities, now.epochSecond)

            appPreferences.setLastSyncTime(now.epochSecond)
            val newToken = healthConnectManager.getChangesToken()
            if (newToken != null) saveChangesToken(newToken)
            emit(Result.success(activities))
        } else {
            val e = fetchResult.exceptionOrNull()!!
            // Stale L1 cache fallback
            if (cache.isNotEmpty()) {
                emit(Result.success(cache.toList()))
                return@flow
            }
            // Room fallback — return persisted data when HC is unreachable
            val roomActivities = activityDao.getActivitiesSince(startEpoch)
            if (roomActivities.isNotEmpty()) {
                val samplesByActivity = activityHrSampleDao
                    .getForActivities(roomActivities.map { it.id })
                    .groupBy { it.activityId }
                    .mapValues { (_, entities) -> entities.map { it.toDomain() } }
                val domain = roomActivities.map { entity ->
                    entity.toDomain(hrSamples = samplesByActivity[entity.id] ?: emptyList())
                }
                cache.clear()
                cache.addAll(domain)
                cacheTime = now
                emit(Result.success(domain))
                return@flow
            }
            val msg = e.message.orEmpty() + (e.cause?.message.orEmpty())
            val isPermissionError = e is SecurityException ||
                e.cause is SecurityException ||
                msg.contains("SecurityException") ||
                msg.contains("permission", ignoreCase = true)
            val isRateLimited = msg.contains("quota", ignoreCase = true) ||
                msg.contains("rate limit", ignoreCase = true)
            val reported = when {
                isRateLimited -> Exception(
                    "Health Connect rate limit reached. Wait a minute and try again.\n" +
                    "(Tip: the previous sync may still be loading — check back shortly.)"
                )
                isPermissionError -> Exception(
                    "Health Connect permissions not granted. Go to Settings → Health Connect → Grant Permissions."
                )
                else -> e
            }
            emit(Result.failure(reported))
        }
    }

    // Session-lifetime memo: HC already confirmed these activity IDs have no HR, avoids repeated re-fetches.
    private val confirmedNoHrIds = mutableSetOf<String>()

    suspend fun getActivityById(id: String): Activity? {
        // L1: skip only if HR absent AND HC hasn't confirmed no-HR for this activity
        cache.firstOrNull { it.id == id }?.let { cached ->
            if (cached.heartRateSamples.isNotEmpty() || id in confirmedNoHrIds) return cached
        }
        // L2: Room — only return when HR rows are present; fall through to HC otherwise
        activityDao.getById(id)?.let { entity ->
            val hrSamples = activityHrSampleDao.getForActivity(id).map { it.toDomain() }
            if (hrSamples.isNotEmpty()) {
                val route = routePointDao.getForActivity(id).map { it.toDomain() }
                return entity.toDomain(hrSamples, route)
            }
        }
        // L3: HC fresh read — backfills Room + L1 so subsequent taps are fast
        val fresh = runCatching { healthConnectManager.readActivityById(id) }.getOrNull() ?: return null
        if (fresh.heartRateSamples.isNotEmpty()) {
            activityHrSampleDao.upsertAll(fresh.heartRateSamples.map { it.toEntity(fresh.id) })
            val idx = cache.indexOfFirst { it.id == fresh.id }
            if (idx >= 0) cache[idx] = fresh else cache.add(fresh)
        } else {
            confirmedNoHrIds.add(id)
        }
        return fresh
    }

    suspend fun getActivityWithRoute(id: String): Pair<Activity?, Boolean> {
        val activity = getActivityById(id) ?: return null to false
        // If route already loaded from Room, return it
        if (activity.route.isNotEmpty()) return activity to false
        val result = runCatching {
            healthConnectManager.readExerciseRoute(id)
        }.getOrDefault(RouteResult())
        return if (result.points.isNotEmpty()) {
            activity.copy(route = result.points) to false
        } else {
            activity to result.consentRequired
        }
    }

    fun invalidateCache() {
        cacheTime = null
    }

    suspend fun importHistory(yearsBack: Int = 5) {
        val now = Instant.now()
        val start = now.minus((yearsBack * 365).toLong(), ChronoUnit.DAYS)
        _importProgress.value = ImportProgress(isRunning = true)
        val result = runCatching {
            healthConnectManager.readActivitiesChunked(
                totalStart = start,
                totalEnd = now,
            ) { loaded, label ->
                _importProgress.value = ImportProgress(isRunning = true, activitiesLoaded = loaded, currentPeriod = label)
            }
        }
        if (result.isSuccess) {
            val imported = tagDuplicates(result.getOrThrow())
            upsertActivitiesToRoom(imported, now.epochSecond)
            val existingIds = cache.map { it.id }.toSet()
            val newOnes = imported.filter { it.id !in existingIds }
            cache.addAll(0, newOnes)
            cacheTime = null
            appPreferences.setHistoryImportedThrough(start.epochSecond)
            appPreferences.setLastSyncTime(now.epochSecond)
        }
        _importProgress.value = ImportProgress(
            isRunning = false,
            activitiesLoaded = if (result.isSuccess) result.getOrThrow().size else 0,
        )
    }

    private suspend fun upsertActivitiesToRoom(activities: List<Activity>, syncedAtEpoch: Long) {
        activityDao.upsertAll(activities.map { it.toEntity(syncedAtEpoch) })
        activities.forEach { activity ->
            if (activity.heartRateSamples.isNotEmpty()) {
                activityHrSampleDao.upsertAll(activity.heartRateSamples.map { it.toEntity(activity.id) })
            }
            if (activity.route.isNotEmpty()) {
                routePointDao.upsertAll(activity.route.map { it.toEntity(activity.id) })
            }
        }
        // Persist sync state so Room freshness check works
        val currentState = syncStateDao.getForType(SYNC_TYPE)
        syncStateDao.upsert(
            SyncStateEntity(
                dataType = SYNC_TYPE,
                changesToken = currentState?.changesToken,
                lastSyncEpoch = syncedAtEpoch,
                lastFullImportEpoch = currentState?.lastFullImportEpoch,
                isFullImportDone = currentState?.isFullImportDone ?: false,
            )
        )
    }

    // Read changes token from Room; fall back to AppPreferences for the first launch after upgrade
    private suspend fun loadChangesToken(): String? {
        val state = syncStateDao.getForType(SYNC_TYPE)
        if (state?.changesToken != null) return state.changesToken
        return appPreferences.hcChangesToken.first()
    }

    // Write changes token to Room and keep AppPreferences in sync for backwards compat
    private suspend fun saveChangesToken(token: String) {
        val current = syncStateDao.getForType(SYNC_TYPE)
        syncStateDao.upsert(
            SyncStateEntity(
                dataType = SYNC_TYPE,
                changesToken = token,
                lastSyncEpoch = current?.lastSyncEpoch,
                lastFullImportEpoch = current?.lastFullImportEpoch,
                isFullImportDone = current?.isFullImportDone ?: false,
            )
        )
        appPreferences.setHcChangesToken(token)
    }

    private fun tagDuplicates(activities: List<Activity>): List<Activity> {
        if (activities.size < 2) return activities
        val dupeIds = mutableSetOf<String>()
        for (i in activities.indices) {
            for (j in i + 1 until activities.size) {
                if (isLikelyDuplicate(activities[i], activities[j])) {
                    dupeIds += activities[i].id
                    dupeIds += activities[j].id
                }
            }
        }
        return if (dupeIds.isEmpty()) activities
        else activities.map { if (it.id in dupeIds) it.copy(hasDuplicate = true) else it }
    }

    private fun isLikelyDuplicate(a: Activity, b: Activity): Boolean {
        if (a.dataOriginPackage == b.dataOriginPackage) return false
        val overlapStart = maxOf(a.startTime, b.startTime)
        val overlapEnd = minOf(a.endTime, b.endTime)
        if (!overlapEnd.isAfter(overlapStart)) return false
        val overlapSec = ChronoUnit.SECONDS.between(overlapStart, overlapEnd).toDouble()
        val shorter = minOf(a.durationSeconds, b.durationSeconds).toDouble()
        return shorter > 0 && overlapSec / shorter > 0.5
    }

    companion object {
        private const val SYNC_TYPE = "ACTIVITIES"
        private const val CACHE_TTL_SECONDS = 300L  // 5 minutes
    }
}
