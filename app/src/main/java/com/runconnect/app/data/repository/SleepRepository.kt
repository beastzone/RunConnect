package com.runconnect.app.data.repository

import com.runconnect.app.data.healthconnect.HealthConnectManager
import com.runconnect.app.domain.model.SleepSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SleepRepository @Inject constructor(
    private val healthConnectManager: HealthConnectManager,
) {
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
}
