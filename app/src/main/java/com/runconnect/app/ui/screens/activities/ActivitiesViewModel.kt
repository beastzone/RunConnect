package com.runconnect.app.ui.screens.activities

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
import javax.inject.Inject

data class ActivitiesUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val activities: List<Activity> = emptyList(),
    val filteredActivities: List<Activity> = emptyList(),
    val selectedFilter: ActivityType? = null,
    val useImperial: Boolean = false,
)

@HiltViewModel
class ActivitiesViewModel @Inject constructor(
    private val activityRepository: ActivityRepository,
    private val appPreferences: AppPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ActivitiesUiState())
    val uiState: StateFlow<ActivitiesUiState> = _uiState

    init {
        loadActivities()
    }

    fun refresh() = loadActivities(forceRefresh = true)

    fun setFilter(type: ActivityType?) {
        val all = _uiState.value.activities
        _uiState.value = _uiState.value.copy(
            selectedFilter = type,
            filteredActivities = if (type == null) all else all.filter { it.type == type },
        )
    }

    private fun loadActivities(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            val useImperial = appPreferences.useImperial.first()
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, useImperial = useImperial)

            activityRepository.getActivities(daysBack = 365, forceRefresh = forceRefresh)
                .collectLatest { result ->
                    result.onSuccess { activities ->
                        val filter = _uiState.value.selectedFilter
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            activities = activities,
                            filteredActivities = if (filter == null) activities
                            else activities.filter { it.type == filter },
                        )
                    }.onFailure { e ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = e.message,
                        )
                    }
                }
        }
    }
}
