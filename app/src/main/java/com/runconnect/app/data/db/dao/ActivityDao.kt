package com.runconnect.app.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.runconnect.app.data.db.entities.ActivityEntity

@Dao
interface ActivityDao {
    @Upsert
    suspend fun upsertAll(activities: List<ActivityEntity>)

    @Query("SELECT * FROM activities WHERE startTimeEpoch >= :startEpoch ORDER BY startTimeEpoch DESC")
    suspend fun getActivitiesSince(startEpoch: Long): List<ActivityEntity>

    @Query("SELECT * FROM activities WHERE id = :id")
    suspend fun getById(id: String): ActivityEntity?

    @Query("SELECT COUNT(*) FROM activities")
    suspend fun count(): Int
}
