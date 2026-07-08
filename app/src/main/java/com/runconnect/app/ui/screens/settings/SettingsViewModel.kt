package com.runconnect.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.runconnect.app.data.healthconnect.HealthConnectManager
import com.runconnect.app.data.preferences.AppPreferences
import com.runconnect.app.data.remote.garmin.GarminAuthManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val useImperial: Boolean = false,
    val maxHeartRate: Int = 190,
    val mapboxToken: String = "",
    val garminConsumerKey: String = "",
    val garminConnected: Boolean = false,
    val healthConnectAvailable: Boolean = false,
    val healthConnectPermissionsGranted: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appPreferences: AppPreferences,
    private val garminAuthManager: GarminAuthManager,
    private val healthConnectManager: HealthConnectManager,
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
                )
            }.collect {}
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                healthConnectPermissionsGranted = healthConnectManager.hasAllPermissions()
            )
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
}
