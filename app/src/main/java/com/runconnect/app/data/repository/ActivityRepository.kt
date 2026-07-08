package com.runconnect.app.data.repository

import com.runconnect.app.data.healthconnect.HealthConnectManager
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

        runCatching {
            val startTime = now.minus(daysBack.toLong(), ChronoUnit.DAYS)
            healthConnectManager.readActivities(startTime, now)
        }.onSuccess { activities ->
            cache.clear()
            cache.addAll(activities)
            cacheTime = now
            emit(Result.success(activities))
        }.onFailure { e ->
            if (cache.isNotEmpty()) {
                emit(Result.success(cache.toList()))
            } else {
                val isPermissionError = e is SecurityException ||
                    e.cause is SecurityException ||
                    e.message?.contains("SecurityException") == true ||
                    e.message?.contains("permission", ignoreCase = true) == true
                val reported = if (isPermissionError)
                    Exception("Health Connect permissions not granted. Go to Settings → Health Connect → Grant Permissions.")
                else e
                emit(Result.failure(reported))
            }
        }
    }

    suspend fun getActivityById(id: String): Activity? {
        cache.firstOrNull { it.id == id }?.let { return it }
        return runCatching { healthConnectManager.readActivityById(id) }.getOrNull()
    }

    // Loads route points for a specific activity on demand (separate from the list load).
    suspend fun getActivityWithRoute(id: String): Activity? {
        val activity = getActivityById(id) ?: return null
        val route = runCatching {
            healthConnectManager.readExerciseRoute(id)
        }.getOrDefault(emptyList())
        return if (route.isNotEmpty()) activity.copy(route = route) else activity
    }

    fun invalidateCache() {
        cache.clear()
        cacheTime = null
    }
}
