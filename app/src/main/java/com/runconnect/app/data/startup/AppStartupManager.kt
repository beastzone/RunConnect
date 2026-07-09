package com.runconnect.app.data.startup

import android.util.Log
import com.runconnect.app.BuildConfig
import com.runconnect.app.data.preferences.AppPreferences
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppStartupManager @Inject constructor(
    private val appPreferences: AppPreferences,
) {
    sealed class StartupType {
        object FirstInstall : StartupType()
        data class Upgrade(val from: Int, val to: Int) : StartupType()
        data class NormalLaunch(val version: Int) : StartupType()
    }

    suspend fun onAppStart() {
        val currentCode = BuildConfig.VERSION_CODE
        val lastCode = appPreferences.lastLaunchedVersionCode.first()
        val firstCode = appPreferences.firstInstallVersionCode.first()

        val type = when {
            firstCode == 0 -> StartupType.FirstInstall
            lastCode < currentCode -> StartupType.Upgrade(lastCode, currentCode)
            else -> StartupType.NormalLaunch(currentCode)
        }

        when (type) {
            is StartupType.FirstInstall -> {
                appPreferences.setFirstInstallVersionCode(currentCode)
                appPreferences.setOnboardingComplete(true)
                appPreferences.setSettingsSchemaVersion(CURRENT_SETTINGS_SCHEMA)
                Log.i(TAG, "First install: version $currentCode")
            }
            is StartupType.Upgrade -> {
                runSettingsMigrations()
                Log.i(TAG, "Upgraded ${type.from} → ${type.to}. Settings preserved.")
            }
            is StartupType.NormalLaunch -> {
                Log.i(TAG, "Normal launch: version ${type.version}")
            }
        }
        appPreferences.setLastLaunchedVersionCode(currentCode)
    }

    private suspend fun runSettingsMigrations() {
        val schemaVersion = appPreferences.settingsSchemaVersion.first()
        if (schemaVersion < 1) {
            // v1 is the baseline schema — nothing to transform, just stamp the version
            appPreferences.setSettingsSchemaVersion(CURRENT_SETTINGS_SCHEMA)
        }
        // Future: if (schemaVersion < 2) { ... }
    }

    companion object {
        const val CURRENT_SETTINGS_SCHEMA = 1
        private const val TAG = "AppStartupManager"
    }
}
