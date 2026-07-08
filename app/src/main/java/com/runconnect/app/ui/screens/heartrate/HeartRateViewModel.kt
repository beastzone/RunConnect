package com.runconnect.app.ui.screens.heartrate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.runconnect.app.data.preferences.AppPreferences
import com.runconnect.app.data.repository.ActivityRepository
import com.runconnect.app.domain.model.HeartRateZoneSummary
import com.runconnect.app.domain.model.HrZone
import com.runconnect.app.domain.model.computeHrZone
import dagger.hilt.android.lifecycle.HiltViewModel
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
    val recentRestingHr: List<Pair<Long, Int>> = emptyList(),
    val maxHrSetting: Int = 190,
)

@HiltViewModel
class HeartRateViewModel @Inject constructor(
    private val activityRepository: ActivityRepository,
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

            activityRepository.getActivities(daysBack = 30).collectLatest { result ->
                result.onSuccess { activities ->
                    val allHrSamples = activities.flatMap { it.heartRateSamples }
                    val allBpms = allHrSamples.map { it.bpm.toInt() }

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

                    val restingHrByActivity = activities
                        .mapNotNull { a -> a.averageHeartRate?.let { a.startTime.epochSecond to it } }

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        avgRestingHr = if (allBpms.isNotEmpty()) allBpms.min() else 0,
                        maxRecordedHr = if (allBpms.isNotEmpty()) allBpms.max() else 0,
                        zoneSummaries = zoneSummaries,
                        recentRestingHr = restingHrByActivity.takeLast(14),
                    )
                }.onFailure { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
                }
            }
        }
    }
}
