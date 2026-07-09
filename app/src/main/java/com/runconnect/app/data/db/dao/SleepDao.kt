package com.runconnect.app.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.runconnect.app.data.db.entities.SleepSessionEntity
import com.runconnect.app.data.db.entities.SleepStageEntity

@Dao
interface SleepDao {
    @Upsert
    suspend fun upsertSessions(sessions: List<SleepSessionEntity>)

    @Upsert
    suspend fun upsertStages(stages: List<SleepStageEntity>)

    @Query("SELECT * FROM sleep_sessions WHERE startTimeEpoch >= :startEpoch ORDER BY startTimeEpoch DESC")
    suspend fun getSessionsSince(startEpoch: Long): List<SleepSessionEntity>

    @Query("SELECT * FROM sleep_stages WHERE sessionId = :sessionId ORDER BY startTimeEpoch ASC")
    suspend fun getStagesForSession(sessionId: String): List<SleepStageEntity>

    @Query("SELECT COUNT(*) FROM sleep_sessions")
    suspend fun count(): Int
}
