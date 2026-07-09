package com.runconnect.app.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "activity_hr_samples",
    primaryKeys = ["activityId", "timestampEpoch"],
    foreignKeys = [ForeignKey(
        entity = ActivityEntity::class,
        parentColumns = ["id"],
        childColumns = ["activityId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("activityId")],
)
data class ActivityHrSampleEntity(
    val activityId: String,
    val timestampEpoch: Long,
    val bpm: Long,
)
