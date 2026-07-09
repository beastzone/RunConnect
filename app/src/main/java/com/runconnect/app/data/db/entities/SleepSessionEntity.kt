package com.runconnect.app.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sleep_sessions",
    indices = [Index("startTimeEpoch"), Index("dataOriginPackage")],
)
data class SleepSessionEntity(
    @PrimaryKey val id: String,
    val startTimeEpoch: Long,
    val endTimeEpoch: Long,
    val dataOriginPackage: String,
    val syncedAtEpoch: Long,
)
