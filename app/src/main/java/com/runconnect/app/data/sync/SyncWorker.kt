package com.runconnect.app.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.runconnect.app.data.preferences.AppPreferences
import com.runconnect.app.data.repository.ActivityRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.Instant

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val activityRepository: ActivityRepository,
    private val appPreferences: AppPreferences,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return runCatching {
            val daysBack = appPreferences.dataDaysBack.first()
            val syncResult = activityRepository.getActivities(daysBack = daysBack, forceRefresh = false).first()
            if (syncResult.isFailure) {
                // Only retry if we have no cached data to fall back on
                return if (runAttemptCount < 2) Result.retry() else Result.failure()
            }
            appPreferences.setLastBackgroundSyncTime(Instant.now().epochSecond)
            Result.success()
        }.getOrElse {
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val WORK_NAME = "runconnect_bg_sync"
    }
}
