package com.runconnect.app.di

import android.content.Context
import androidx.room.Room
import com.runconnect.app.data.db.AppDatabase
import com.runconnect.app.data.db.dao.ActivityDao
import com.runconnect.app.data.db.dao.ActivityHrSampleDao
import com.runconnect.app.data.db.dao.RoutePointDao
import com.runconnect.app.data.db.dao.SleepDao
import com.runconnect.app.data.db.dao.SyncStateDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
            // Never add fallbackToDestructiveMigration — always provide explicit Migration objects
            .build()

    @Provides
    fun provideActivityDao(db: AppDatabase): ActivityDao = db.activityDao()

    @Provides
    fun provideActivityHrSampleDao(db: AppDatabase): ActivityHrSampleDao = db.activityHrSampleDao()

    @Provides
    fun provideRoutePointDao(db: AppDatabase): RoutePointDao = db.routePointDao()

    @Provides
    fun provideSleepDao(db: AppDatabase): SleepDao = db.sleepDao()

    @Provides
    fun provideSyncStateDao(db: AppDatabase): SyncStateDao = db.syncStateDao()
}
