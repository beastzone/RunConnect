package com.runconnect.app.data.repository

import com.runconnect.app.data.db.dao.SleepDao
import com.runconnect.app.data.db.dao.SyncStateDao
import com.runconnect.app.data.db.entities.SyncStateEntity
import com.runconnect.app.data.db.mappers.toDomain
import com.runconnect.app.data.db.mappers.toEntity
import com.runconnect.app.data.healthconnect.HealthConnectManager
import com.runconnect.app.data.preferences.AppPreferences
import com.runconnect.app.domain.model.SleepAnnotation
import com.runconnect.app.domain.model.SleepSession
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SleepRepository @Inject constructor(
    private val healthConnectManager: HealthConnectManager,
    private val appPreferences: AppPreferences,
    private val moshi: Moshi,
    private val sleepDao: SleepDao,
    private val syncStateDao: SyncStateDao,
) {
    private val annotationAdapter: JsonAdapter<Map<String, SleepAnnotation>> by lazy {
        val type = Types.newParameterizedType(Map::class.java, String::class.java, SleepAnnotation::class.java)
        moshi.adapter(type)
    }

    fun getSleepSessions(daysBack: Int = 30): Flow<Result<List<SleepSession>>> = flow {
        val now = Instant.now()
        val startEpoch = now.minus(daysBack.toLong(), ChronoUnit.DAYS).epochSecond

        // HC fetch — primary source
        val fetchResult = runCatching {
            healthConnectManager.readSleepSessions(Instant.ofEpochSecond(startEpoch), now)
        }
        if (fetchResult.isSuccess) {
            val sessions = fetchResult.getOrThrow().sortedByDescending { it.startTime }
            upsertSessionsToRoom(sessions, now.epochSecond)
            emit(Result.success(sessions))
        } else {
            // Room fallback — biometric samples (HR/HRV/SpO2) will be empty but stage data survives
            val roomSessions = loadSessionsFromRoom(startEpoch)
            if (roomSessions.isNotEmpty()) {
                emit(Result.success(roomSessions))
            } else {
                emit(Result.failure(fetchResult.exceptionOrNull()!!))
            }
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

    private suspend fun upsertSessionsToRoom(sessions: List<SleepSession>, syncedAtEpoch: Long) {
        sleepDao.upsertSessions(sessions.map { it.toEntity(syncedAtEpoch) })
        sessions.forEach { session ->
            sleepDao.upsertStages(session.stages.map { it.toEntity(session.id) })
        }
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

    private suspend fun loadSessionsFromRoom(startEpoch: Long): List<SleepSession> {
        val entities = sleepDao.getSessionsSince(startEpoch)
        return entities.map { entity ->
            val stages = sleepDao.getStagesForSession(entity.id).map { it.toDomain() }
            entity.toDomain(stages)
        }.sortedByDescending { it.startTime }
    }

    companion object {
        private const val SYNC_TYPE = "SLEEP"
    }
}
