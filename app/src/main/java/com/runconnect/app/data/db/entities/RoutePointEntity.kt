package com.runconnect.app.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "route_points",
    primaryKeys = ["activityId", "timestampEpoch"],
    foreignKeys = [ForeignKey(
        entity = ActivityEntity::class,
        parentColumns = ["id"],
        childColumns = ["activityId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("activityId")],
)
data class RoutePointEntity(
    val activityId: String,
    val timestampEpoch: Long,
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double?,
    val heartRate: Int?,
    val speedMps: Double?,
)
