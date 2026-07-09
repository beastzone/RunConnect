package com.runconnect.app.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.runconnect.app.data.db.entities.SyncStateEntity

@Dao
interface SyncStateDao {
    @Upsert
    suspend fun upsert(state: SyncStateEntity)

    @Query("SELECT * FROM sync_state WHERE dataType = :type")
    suspend fun getForType(type: String): SyncStateEntity?
}
