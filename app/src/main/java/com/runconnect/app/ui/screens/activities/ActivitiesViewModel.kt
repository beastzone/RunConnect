package com.runconnect.app.ui.screens.activities

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.runconnect.app.data.preferences.AppPreferences
import com.runconnect.app.data.repository.ActivityRepository
import com.runconnect.app.domain.model.Activity
import com.runconnect.app.domain.model.ActivityType
import com.runconnect.app.ui.components.packageToDisplayName
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
    val sourceFilter: String? = null,
    val availableSources: List<Pair<String, String>> = emptyList(),
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
        _uiState.value = _uiState.value.copy(selectedFilter = type)
        applyFilters()
    }

    fun setSourceFilter(packageName: String?) {
        _uiState.value = _uiState.value.copy(sourceFilter = packageName)
        applyFilters()
    }

    private fun applyFilters() {
        val state = _uiState.value
        val filtered = state.activities
            .let { if (state.selectedFilter != null) it.filter { a -> a.type == state.selectedFilter } else it }
            .let { if (state.sourceFilter != null) it.filter { a -> a.dataOriginPackage == state.sourceFilter } else it }
        _uiState.value = state.copy(filteredActivities = filtered)
    }

    private fun loadActivities(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            val useImperial = appPreferences.useImperial.first()
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, useImperial = useImperial)

            activityRepository.getActivities(daysBack = 365, forceRefresh = forceRefresh)
                .collectLatest { result ->
                    result.onSuccess { activities ->
                        val sources = activities
                            .map { it.dataOriginPackage }
                            .filter { it.isNotEmpty() }
                            .distinct()
                            .map { it to packageToDisplayName(it) }
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            activities = activities,
                            availableSources = sources,
                        )
                        applyFilters()
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
