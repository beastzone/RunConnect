package com.runconnect.app.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val dataType: String,   // "ACTIVITIES", "SLEEP", "BODY"
    val changesToken: String?,
    val lastSyncEpoch: Long?,
    val lastFullImportEpoch: Long?,
    val isFullImportDone: Boolean,
)
