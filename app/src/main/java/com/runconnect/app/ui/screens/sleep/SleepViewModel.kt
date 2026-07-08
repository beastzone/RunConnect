package com.runconnect.app.ui.screens.sleep

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.runconnect.app.data.repository.SleepRepository
import com.runconnect.app.domain.model.SleepSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SleepUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val sessions: List<SleepSession> = emptyList(),
    val avgDurationMinutes: Long = 0L,
    val avgDeepMinutes: Long = 0L,
    val avgRemMinutes: Long = 0L,
    val avgEfficiency: Int = 0,
)

@HiltViewModel
class SleepViewModel @Inject constructor(
    private val sleepRepository: SleepRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SleepUiState())
    val uiState: StateFlow<SleepUiState> = _uiState

    init {
        loadSleep()
    }

    fun refresh() = loadSleep()

    private fun loadSleep() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            sleepRepository.getSleepSessions(30).collectLatest { result ->
                result.onSuccess { sessions ->
                    val avg = { fn: (SleepSession) -> Long ->
                        if (sessions.isEmpty()) 0L else sessions.map(fn).average().toLong()
                    }
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        sessions = sessions,
                        avgDurationMinutes = avg { it.totalDurationMinutes },
                        avgDeepMinutes = avg { it.deepSleepMinutes },
                        avgRemMinutes = avg { it.remSleepMinutes },
                        avgEfficiency = if (sessions.isEmpty()) 0
                        else sessions.map { it.sleepEfficiencyPercent }.average().toInt(),
                    )
                }.onFailure { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
                }
            }
        }
    }
}
