package com.runconnect.app.ui.screens.heartrate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.runconnect.app.data.healthconnect.HealthConnectManager
import com.runconnect.app.data.preferences.AppPreferences
import com.runconnect.app.data.repository.ActivityRepository
import com.runconnect.app.domain.model.HeartRateZoneSummary
import com.runconnect.app.domain.model.HrZone
import com.runconnect.app.domain.model.computeHrZone
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HeartRateUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val avgRestingHr: Int = 0,
    val maxRecordedHr: Int = 0,
    val zoneSummaries: List<HeartRateZoneSummary> = emptyList(),
    val restingHrHistory: List<Pair<Long, Int>> = emptyList(),
    val hrvHistory: List<Pair<Long, Double>> = emptyList(),
    val maxHrSetting: Int = 190,
)

@HiltViewModel
class HeartRateViewModel @Inject constructor(
    private val activityRepository: ActivityRepository,
    private val healthConnectManager: HealthConnectManager,
    private val appPreferences: AppPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HeartRateUiState())
    val uiState: StateFlow<HeartRateUiState> = _uiState

    init {
        loadData()
    }

    fun refresh() = loadData()

    private fun loadData() {
        viewModelScope.launch {
            val maxHr = appPreferences.maxHeartRate.first()
            _uiState.value = _uiState.value.copy(isLoading = true, maxHrSetting = maxHr)

            // Load dedicated resting HR + HRV records in parallel with activity load
            val restingHrDeferred = async {
                runCatching { healthConnectManager.readRestingHeartRateHistory(30) }.getOrDefault(emptyList())
            }
            val hrvDeferred = async {
                runCatching { healthConnectManager.readHrvHistory(30) }.getOrDefault(emptyList())
            }

            activityRepository.getActivities(daysBack = 30).collectLatest { result ->
                val restingHrHistory = restingHrDeferred.await()
                val hrvRaw = hrvDeferred.await()

                result.onSuccess { activities ->
                    val zoneMinutes = mutableMapOf<HrZone, Long>()
                    activities.forEach { activity ->
                        activity.heartRateSamples.forEach { sample ->
                            val zone = computeHrZone(sample.bpm.toInt(), maxHr)
                            zoneMinutes[zone] = (zoneMinutes[zone] ?: 0L) + 1
                        }
                    }
                    val totalMinutes = zoneMinutes.values.sum().coerceAtLeast(1L)

                    val zoneSummaries = HrZone.values().map { zone ->
                        val mins = zoneMinutes[zone] ?: 0L
                        HeartRateZoneSummary(zone, mins, mins.toFloat() / totalMinutes)
                    }

                    val allBpms = activities.flatMap { it.heartRateSamples }.map { it.bpm.toInt() }

                    // Use dedicated RHR records if available; fall back to activity minimum
                    val avgRhr = if (restingHrHistory.isNotEmpty())
                        restingHrHistory.map { it.second }.average().toInt()
                    else if (allBpms.isNotEmpty()) allBpms.min() else 0

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        avgRestingHr = avgRhr,
                        maxRecordedHr = if (allBpms.isNotEmpty()) allBpms.max() else 0,
                        zoneSummaries = zoneSummaries,
                        restingHrHistory = restingHrHistory
                            .sortedBy { it.first }
                            .takeLast(30)
                            .map { it.first.epochSecond to it.second },
                        hrvHistory = hrvRaw
                            .sortedBy { it.first }
                            .takeLast(30)
                            .map { it.first.epochSecond to it.second },
                    )
                }.onFailure { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
                }
            }
        }
    }
}
