package com.runconnect.app.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "activities",
    indices = [Index("dataOriginPackage"), Index("startTimeEpoch")],
)
data class ActivityEntity(
    @PrimaryKey val id: String,
    val type: String,                   // ActivityType.name
    val title: String,
    val startTimeEpoch: Long,
    val endTimeEpoch: Long,
    val durationSeconds: Long,
    val distanceMeters: Double,
    val elevationGainMeters: Double,
    val averageHeartRate: Int?,
    val maxHeartRate: Int?,
    val averageSpeedMps: Double?,
    val calories: Int?,
    val lapsJson: String,               // JSON-encoded List<LapData>
    val dataOriginPackage: String,
    val garminActivityId: Long?,
    val completenessScore: Int,
    val hasDuplicate: Boolean,
    val startZoneOffsetId: String?,     // ZoneOffset.id, e.g. "+05:30"
    val syncedAtEpoch: Long,
)
