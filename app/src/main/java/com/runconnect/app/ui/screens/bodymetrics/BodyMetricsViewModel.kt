package com.runconnect.app.ui.screens.bodymetrics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.runconnect.app.data.preferences.AppPreferences
import com.runconnect.app.data.repository.BodyMetricsRepository
import com.runconnect.app.domain.model.BodyMetricsSample
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BodyMetricsUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val samples: List<BodyMetricsSample> = emptyList(),
    val latestWeightKg: Double? = null,
    val latestBodyFatPct: Double? = null,
    val weightChangeKg: Double? = null,
    val bodyFatChangePct: Double? = null,
    val useImperial: Boolean = false,
)

@HiltViewModel
class BodyMetricsViewModel @Inject constructor(
    private val bodyMetricsRepository: BodyMetricsRepository,
    private val appPreferences: AppPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BodyMetricsUiState())
    val uiState: StateFlow<BodyMetricsUiState> = _uiState

    init {
        loadData()
    }

    fun refresh() = loadData()

    private fun loadData() {
        viewModelScope.launch {
            val useImperial = appPreferences.useImperial.first()
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, useImperial = useImperial)

            val samples = runCatching { bodyMetricsRepository.getBodyMetrics(90) }.getOrElse { e ->
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
                return@launch
            }

            val weights = samples.mapNotNull { it.weightKg }
            val fats = samples.mapNotNull { it.bodyFatPercent }

            val latestWeight = weights.firstOrNull()
            val oldestWeight = weights.lastOrNull()
            val latestFat = fats.firstOrNull()
            val oldestFat = fats.lastOrNull()

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                samples = samples,
                latestWeightKg = latestWeight,
                latestBodyFatPct = latestFat,
                weightChangeKg = if (latestWeight != null && oldestWeight != null && latestWeight != oldestWeight)
                    latestWeight - oldestWeight else null,
                bodyFatChangePct = if (latestFat != null && oldestFat != null && latestFat != oldestFat)
                    latestFat - oldestFat else null,
                useImperial = useImperial,
            )
        }
    }
}
