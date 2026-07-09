package com.runconnect.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.work.Configuration
import com.runconnect.app.data.startup.AppStartupManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class RunConnectApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var startupManager: AppStartupManager

    override fun onCreate() {
        super.onCreate()
        // Non-blocking — startup writes version tracking and runs settings migrations
        // asynchronously; UI proceeds normally before it completes.
        ProcessLifecycleOwner.get().lifecycleScope.launch(Dispatchers.IO) {
            startupManager.onAppStart()
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
}
