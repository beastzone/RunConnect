package com.runconnect.app.ui.screens.settings

import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.runconnect.app.data.connectivity.ConnectivityRepository
import com.runconnect.app.data.healthconnect.HealthConnectManager
import com.runconnect.app.data.healthconnect.PermissionInfo
import com.runconnect.app.data.preferences.AppPreferences
import com.runconnect.app.data.remote.garmin.GarminAuthManager
import com.runconnect.app.data.repository.ActivityRepository
import com.runconnect.app.data.repository.ImportProgress
import com.runconnect.app.data.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

data class SettingsUiState(
    val useImperial: Boolean = false,
    val maxHeartRate: Int = 190,
    val mapboxToken: String = "",
    val garminConsumerKey: String = "",
    val garminConnected: Boolean = false,
    val healthConnectAvailable: Boolean = false,
    val healthConnectSdkStatus: Int = -1,
    val healthConnectPermissionsGranted: Boolean = false,
    val healthConnectGrantedCount: Int = 0,
    val healthConnectRequiredCount: Int = 0,
    val lastSyncLabel: String = "Never",
    val isSyncing: Boolean = false,
    val dataDaysBack: Int = 90,
    val backgroundSyncEnabled: Boolean = false,
    val hcChangesTokenPresent: Boolean = false,
    val cacheActivityCount: Int = 0,
    val lastBackgroundSyncLabel: String = "Never",
    val permissionStatuses: List<Pair<PermissionInfo, Boolean>> = emptyList(),
    val lastSyncSummary: String? = null,
    val importProgress: ImportProgress = ImportProgress(),
    val historyImportedLabel: String? = null,
    val isOffline: Boolean = false,
) {
    val healthConnectStatusLabel: String get() = when (healthConnectSdkStatus) {
        HealthConnectClient.SDK_AVAILABLE -> "Available (SDK ${healthConnectSdkStatus})"
        HealthConnectClient.SDK_UNAVAILABLE -> "Not installed (SDK ${healthConnectSdkStatus})"
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> "Needs update (SDK ${healthConnectSdkStatus})"
        else -> "Unknown (SDK ${healthConnectSdkStatus})"
    }
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appPreferences: AppPreferences,
    private val garminAuthManager: GarminAuthManager,
    private val healthConnectManager: HealthConnectManager,
    private val activityRepository: ActivityRepository,
    private val syncScheduler: SyncScheduler,
    private val connectivityRepository: ConnectivityRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState

    init {
        viewModelScope.launch {
            combine(
                appPreferences.useImperial,
                appPreferences.maxHeartRate,
                appPreferences.mapboxToken,
                garminAuthManager.isAuthenticated,
            ) { imperial, maxHr, mapbox, garminAuth ->
                _uiState.value = _uiState.value.copy(
                    useImperial = imperial,
                    maxHeartRate = maxHr,
                    mapboxToken = mapbox,
                    garminConnected = garminAuth,
                    healthConnectAvailable = healthConnectManager.isAvailable,
                    healthConnectSdkStatus = healthConnectManager.sdkStatus,
                )
            }.collect {}
        }
        viewModelScope.launch {
            appPreferences.lastSyncTime.collect { epochSeconds ->
                _uiState.value = _uiState.value.copy(lastSyncLabel = formatLastSync(epochSeconds))
            }
        }
        viewModelScope.launch {
            appPreferences.dataDaysBack.collect { days ->
                _uiState.value = _uiState.value.copy(dataDaysBack = days)
            }
        }
        viewModelScope.launch {
            appPreferences.backgroundSyncEnabled.collect { enabled ->
                _uiState.value = _uiState.value.copy(backgroundSyncEnabled = enabled)
            }
        }
        viewModelScope.launch {
            appPreferences.hcChangesToken.collect { token ->
                _uiState.value = _uiState.value.copy(hcChangesTokenPresent = token != null)
            }
        }
        viewModelScope.launch {
            appPreferences.lastBackgroundSyncTime.collect { epochSeconds ->
                _uiState.value = _uiState.value.copy(lastBackgroundSyncLabel = formatLastSync(epochSeconds))
            }
        }
        viewModelScope.launch {
            activityRepository.importProgress.collect { progress ->
                _uiState.value = _uiState.value.copy(importProgress = progress)
            }
        }
        viewModelScope.launch {
            appPreferences.historyImportedThrough.collect { epoch ->
                _uiState.value = _uiState.value.copy(
                    historyImportedLabel = epoch?.let {
                        val date = Instant.ofEpochSecond(it).atZone(ZoneId.systemDefault())
                        val mon = date.month.name.lowercase().replaceFirstChar { c -> c.uppercase() }.take(3)
                        "Imported from $mon ${date.year}"
                    }
                )
            }
        }
        viewModelScope.launch { refreshPermissionsInternal() }
        viewModelScope.launch {
            connectivityRepository.isOnline.collect { online ->
                _uiState.value = _uiState.value.copy(isOffline = !online)
            }
        }
    }

    fun setUseImperial(value: Boolean) = viewModelScope.launch {
        appPreferences.setUseImperial(value)
    }

    fun setMaxHeartRate(bpm: Int) = viewModelScope.launch {
        appPreferences.setMaxHeartRate(bpm)
    }

    fun setMapboxToken(token: String) = viewModelScope.launch {
        appPreferences.setMapboxToken(token)
    }

    fun saveGarminCredentials(consumerKey: String, consumerSecret: String) = viewModelScope.launch {
        garminAuthManager.saveCredentials(consumerKey, consumerSecret)
    }

    fun connectGarmin() = viewModelScope.launch {
        garminAuthManager.startOAuthFlow()
    }

    fun disconnectGarmin() = viewModelScope.launch {
        garminAuthManager.signOut()
    }

    fun syncNow() = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(isSyncing = true, lastSyncSummary = null)
        val daysBack = appPreferences.dataDaysBack.first()
        val prevIds = activityRepository.getCachedIds()
        activityRepository.invalidateCache()
        runCatching {
            activityRepository.getActivities(daysBack = daysBack, forceRefresh = true).first()
        }
        val newIds = activityRepository.getCachedIds()
        val added = (newIds - prevIds).size
        val removed = (prevIds - newIds).size
        val total = newIds.size
        val summary = when {
            added > 0 && removed > 0 -> "$added new, $removed removed"
            added > 0 -> "$added new ${if (added == 1) "activity" else "activities"}"
            removed > 0 -> "$removed ${if (removed == 1) "activity" else "activities"} removed"
            else -> "Up to date · $total ${if (total == 1) "activity" else "activities"}"
        }
        val syncEpoch = appPreferences.lastSyncTime.first()
        _uiState.value = _uiState.value.copy(
            isSyncing = false,
            lastSyncLabel = formatLastSync(syncEpoch),
            cacheActivityCount = activityRepository.cacheSize,
            lastSyncSummary = summary,
        )
    }

    fun importHistory(yearsBack: Int = 5) = viewModelScope.launch {
        activityRepository.importHistory(yearsBack)
        _uiState.value = _uiState.value.copy(cacheActivityCount = activityRepository.cacheSize)
    }

    fun setBackgroundSyncEnabled(enabled: Boolean) = viewModelScope.launch {
        appPreferences.setBackgroundSyncEnabled(enabled)
        if (enabled) syncScheduler.scheduleBackgroundSync() else syncScheduler.cancelBackgroundSync()
    }

    fun setDataDaysBack(days: Int) = viewModelScope.launch {
        appPreferences.setDataDaysBack(days)
    }

    val requiredPermissions: Set<String>
        get() = healthConnectManager.requiredPermissions

    fun refreshPermissions() = viewModelScope.launch { refreshPermissionsInternal() }

    private suspend fun refreshPermissionsInternal() {
        val granted = runCatching { healthConnectManager.checkPermissions() }.getOrDefault(emptySet())
        val required = healthConnectManager.requiredPermissions
        val permStatuses = healthConnectManager.permissionInfoList.map { it to (it.permission in granted) }
        _uiState.value = _uiState.value.copy(
            healthConnectSdkStatus = healthConnectManager.sdkStatus,
            healthConnectAvailable = healthConnectManager.isAvailable,
            healthConnectPermissionsGranted = required.all { it in granted },
            healthConnectGrantedCount = granted.count { it in required },
            healthConnectRequiredCount = required.size,
            cacheActivityCount = activityRepository.cacheSize,
            permissionStatuses = permStatuses,
        )
    }

    private fun formatLastSync(epochSeconds: Long?): String {
        if (epochSeconds == null) return "Never"
        val now = Instant.now().epochSecond
        val diff = now - epochSeconds
        return when {
            diff < 60 -> "Just now"
            diff < 3600 -> "${diff / 60}m ago"
            diff < 86400 -> "${diff / 3600}h ago"
            else -> {
                val date = Instant.ofEpochSecond(epochSeconds)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                val month = date.month.name.lowercase().replaceFirstChar { it.uppercase() }.substring(0, 3)
                "$month ${date.dayOfMonth}"
            }
        }
    }
}
