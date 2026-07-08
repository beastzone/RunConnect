package com.runconnect.app.data.repository

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
) {
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
        val cacheExpired = cacheTime?.let {
            now.isAfter(it.plus(5, ChronoUnit.MINUTES))
        } ?: true

        // Fresh cache — return immediately (no HC calls)
        if (!forceRefresh && !cacheExpired && cache.isNotEmpty()) {
            emit(Result.success(cache.toList()))
            return@flow
        }

        // Stale cache — check HC change log before doing a full 6-call fetch.
        // If nothing changed, renew the token and extend the cache lifetime for free.
        if (!forceRefresh && cache.isNotEmpty()) {
            val savedToken = appPreferences.hcChangesToken.first()
            if (savedToken != null) {
                val summary = healthConnectManager.processChanges(savedToken)
                if (summary != null && !summary.hasExerciseChanges) {
                    appPreferences.setHcChangesToken(summary.newToken)
                    cacheTime = now
                    emit(Result.success(cache.toList()))
                    return@flow
                }
                if (summary != null) appPreferences.setHcChangesToken(summary.newToken)
                // summary == null means token expired — fall through to full fetch
            }
        }

        val fetchResult = runCatching {
            val startTime = now.minus(daysBack.toLong(), ChronoUnit.DAYS)
            healthConnectManager.readActivities(startTime, now)
        }
        if (fetchResult.isSuccess) {
            val activities = tagDuplicates(fetchResult.getOrThrow())
            cache.clear()
            cache.addAll(activities)
            cacheTime = now
            appPreferences.setLastSyncTime(now.epochSecond)
            val newToken = healthConnectManager.getChangesToken()
            if (newToken != null) appPreferences.setHcChangesToken(newToken)
            emit(Result.success(activities))
        } else {
            val e = fetchResult.exceptionOrNull()!!
            // Always return stale cache on any fetch failure so the UI keeps showing data
            if (cache.isNotEmpty()) {
                emit(Result.success(cache.toList()))
            } else {
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
    }

    suspend fun getActivityById(id: String): Activity? {
        cache.firstOrNull { it.id == id }?.let { return it }
        return runCatching { healthConnectManager.readActivityById(id) }.getOrNull()
    }

    // Returns (activity with route if available, consentRequired flag).
    suspend fun getActivityWithRoute(id: String): Pair<Activity?, Boolean> {
        val activity = getActivityById(id) ?: return null to false
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
        cacheTime = null  // Mark stale — cache data survives as fallback if next fetch fails
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

    // Tags activities that appear to be the same workout recorded by different apps.
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
}
