package com.runconnect.app.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.runconnect.app.data.db.entities.ActivityHrSampleEntity

@Dao
interface ActivityHrSampleDao {
    @Upsert
    suspend fun upsertAll(samples: List<ActivityHrSampleEntity>)

    @Query("SELECT * FROM activity_hr_samples WHERE activityId = :activityId ORDER BY timestampEpoch ASC")
    suspend fun getForActivity(activityId: String): List<ActivityHrSampleEntity>
}
