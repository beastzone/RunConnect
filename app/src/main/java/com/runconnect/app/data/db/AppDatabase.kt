package com.runconnect.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.runconnect.app.data.db.dao.ActivityDao
import com.runconnect.app.data.db.dao.ActivityHrSampleDao
import com.runconnect.app.data.db.dao.RoutePointDao
import com.runconnect.app.data.db.dao.SleepDao
import com.runconnect.app.data.db.dao.SyncStateDao
import com.runconnect.app.data.db.entities.ActivityEntity
import com.runconnect.app.data.db.entities.ActivityHrSampleEntity
import com.runconnect.app.data.db.entities.RoutePointEntity
import com.runconnect.app.data.db.entities.SleepSessionEntity
import com.runconnect.app.data.db.entities.SleepStageEntity
import com.runconnect.app.data.db.entities.SyncStateEntity

@Database(
    entities = [
        ActivityEntity::class,
        ActivityHrSampleEntity::class,
        RoutePointEntity::class,
        SleepSessionEntity::class,
        SleepStageEntity::class,
        SyncStateEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun activityDao(): ActivityDao
    abstract fun activityHrSampleDao(): ActivityHrSampleDao
    abstract fun routePointDao(): RoutePointDao
    abstract fun sleepDao(): SleepDao
    abstract fun syncStateDao(): SyncStateDao

    companion object {
        const val VERSION = 1
        const val DATABASE_NAME = "runconnect_db"
    }
}
