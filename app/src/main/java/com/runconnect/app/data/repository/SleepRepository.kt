package com.runconnect.app.data.repository

import com.runconnect.app.data.healthconnect.HealthConnectManager
import com.runconnect.app.data.preferences.AppPreferences
import com.runconnect.app.domain.model.SleepAnnotation
import com.runconnect.app.domain.model.SleepSession
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SleepRepository @Inject constructor(
    private val healthConnectManager: HealthConnectManager,
    private val appPreferences: AppPreferences,
    private val moshi: Moshi,
) {
    private val annotationAdapter: JsonAdapter<Map<String, SleepAnnotation>> by lazy {
        val type = Types.newParameterizedType(Map::class.java, String::class.java, SleepAnnotation::class.java)
        moshi.adapter(type)
    }

    fun getSleepSessions(daysBack: Int = 30): Flow<Result<List<SleepSession>>> = flow {
        val now = Instant.now()
        val startTime = now.minus(daysBack.toLong(), ChronoUnit.DAYS)
        runCatching {
            healthConnectManager.readSleepSessions(startTime, now)
        }.onSuccess { sessions ->
            emit(Result.success(sessions.sortedByDescending { it.startTime }))
        }.onFailure { e ->
            emit(Result.failure(e))
        }
    }

    fun getAnnotationsFlow(): Flow<Map<String, SleepAnnotation>> =
        appPreferences.sleepAnnotationsJson.map { json ->
            runCatching { annotationAdapter.fromJson(json) ?: emptyMap() }.getOrDefault(emptyMap())
        }

    suspend fun setAnnotation(sessionId: String, annotation: SleepAnnotation) {
        val current = appPreferences.sleepAnnotationsJson.first()
            .let { runCatching { annotationAdapter.fromJson(it) ?: emptyMap() }.getOrDefault(emptyMap()) }
            .toMutableMap()
        current[sessionId] = annotation
        appPreferences.setSleepAnnotationsJson(annotationAdapter.toJson(current))
    }
}
