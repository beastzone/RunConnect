package com.runconnect.app.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.runconnect.app.data.db.entities.RoutePointEntity

@Dao
interface RoutePointDao {
    @Upsert
    suspend fun upsertAll(points: List<RoutePointEntity>)

    @Query("SELECT * FROM route_points WHERE activityId = :activityId ORDER BY timestampEpoch ASC")
    suspend fun getForActivity(activityId: String): List<RoutePointEntity>
}
