package com.runconnect.app.data.repository

import com.runconnect.app.data.healthconnect.HealthConnectManager
import com.runconnect.app.data.healthconnect.RouteResult
import com.runconnect.app.data.preferences.AppPreferences
import com.runconnect.app.domain.model.Activity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActivityRepository @Inject constructor(
    private val healthConnectManager: HealthConnectManager,
    private val appPreferences: AppPreferences,
) {
    private val cache = mutableListOf<Activity>()
    private var cacheTime: Instant? = null

    fun getActivities(
        daysBack: Int = 90,
        forceRefresh: Boolean = false,
    ): Flow<Result<List<Activity>>> = flow {
        val now = Instant.now()
        val cacheExpired = cacheTime?.let {
            now.isAfter(it.plus(5, ChronoUnit.MINUTES))
        } ?: true

        if (!forceRefresh && !cacheExpired && cache.isNotEmpty()) {
            emit(Result.success(cache.toList()))
            return@flow
        }

        val fetchResult = runCatching {
            val startTime = now.minus(daysBack.toLong(), ChronoUnit.DAYS)
            healthConnectManager.readActivities(startTime, now)
        }
        if (fetchResult.isSuccess) {
            val activities = fetchResult.getOrThrow()
            cache.clear()
            cache.addAll(activities)
            cacheTime = now
            appPreferences.setLastSyncTime(now.epochSecond)
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
}
