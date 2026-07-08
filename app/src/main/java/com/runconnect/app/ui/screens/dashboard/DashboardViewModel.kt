package com.runconnect.app.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.runconnect.app.data.preferences.AppPreferences
import com.runconnect.app.data.repository.ActivityRepository
import com.runconnect.app.domain.model.Activity
import com.runconnect.app.domain.model.ActivityType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class DashboardUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val recentActivities: List<Activity> = emptyList(),
    val weeklyDistanceKm: Double = 0.0,
    val weeklyActivityCount: Int = 0,
    val weeklyTimeSeconds: Long = 0L,
    val streakDays: Int = 0,
    val useImperial: Boolean = false,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val activityRepository: ActivityRepository,
    private val appPreferences: AppPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState

    init {
        loadData()
    }

    fun refresh() = loadData(forceRefresh = true)

    private fun loadData(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            val useImperial = appPreferences.useImperial.first()
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, useImperial = useImperial)

            activityRepository.getActivities(daysBack = 90, forceRefresh = forceRefresh)
                .collectLatest { result ->
                    result.onSuccess { activities ->
                        val weekStart = LocalDate.now().minusDays(7)
                            .atStartOfDay(ZoneId.systemDefault()).toInstant()
                        val weekActivities = activities.filter { it.startTime.isAfter(weekStart) }

                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            recentActivities = activities.take(5),
                            weeklyDistanceKm = weekActivities.sumOf { it.distanceMeters } / 1000.0,
                            weeklyActivityCount = weekActivities.size,
                            weeklyTimeSeconds = weekActivities.sumOf { it.durationSeconds },
                            streakDays = computeStreak(activities),
                        )
                    }.onFailure { e ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = e.message ?: "Failed to load activities",
                        )
                    }
                }
        }
    }

    private fun computeStreak(activities: List<Activity>): Int {
        val activeDays = activities
            .map { it.startTime.atZone(ZoneId.systemDefault()).toLocalDate() }
            .toSet()

        var streak = 0
        var checkDate = LocalDate.now()
        while (activeDays.contains(checkDate)) {
            streak++
            checkDate = checkDate.minusDays(1)
        }
        return streak
    }
}
