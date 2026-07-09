package com.runconnect.app.ui.screens.sleep

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.runconnect.app.data.healthconnect.HealthConnectManager
import com.runconnect.app.data.preferences.AppPreferences
import com.runconnect.app.data.repository.SleepRepository
import com.runconnect.app.domain.model.SleepAnnotation
import com.runconnect.app.domain.model.SleepSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.ZoneId
import javax.inject.Inject

data class SleepDetailUiState(
    val isLoading: Boolean = true,
    val session: SleepSession? = null,
    val annotation: SleepAnnotation = SleepAnnotation(),
    val historicalAvgDeep: Long = 0L,
    val historicalAvgLight: Long = 0L,
    val historicalAvgRem: Long = 0L,
    val historicalAvgScore: Int = 0,
    val useImperial: Boolean = false,
)

@HiltViewModel
class SleepDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sleepRepository: SleepRepository,
    private val healthConnectManager: HealthConnectManager,
    private val appPreferences: AppPreferences,
) : ViewModel() {

    private val sessionId: String = checkNotNull(savedStateHandle["sessionId"])

    private val _uiState = MutableStateFlow(SleepDetailUiState())
    val uiState: StateFlow<SleepDetailUiState> = _uiState

    init {
        viewModelScope.launch {
            appPreferences.useImperial.collect { imperial ->
                _uiState.value = _uiState.value.copy(useImperial = imperial)
            }
        }
        viewModelScope.launch {
            sleepRepository.getSleepSessions(daysBack = 90).collectLatest { result ->
                result.onSuccess { sessions ->
                    val session = sessions.find { it.id == sessionId }
                    val mainSessions = sessions.filterNot { it.isNap }
                    val avgDeep = if (mainSessions.size > 1) mainSessions.filter { it.id != sessionId }.map { it.deepSleepMinutes }.average().toLong() else 0L
                    val avgLight = if (mainSessions.size > 1) mainSessions.filter { it.id != sessionId }.map { it.lightSleepMinutes }.average().toLong() else 0L
                    val avgRem = if (mainSessions.size > 1) mainSessions.filter { it.id != sessionId }.map { it.remSleepMinutes }.average().toLong() else 0L
                    val avgScore = if (mainSessions.size > 1) mainSessions.filter { it.id != sessionId }.map { it.sleepScore }.average().toInt() else 0
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        session = session,
                        historicalAvgDeep = avgDeep,
                        historicalAvgLight = avgLight,
                        historicalAvgRem = avgRem,
                        historicalAvgScore = avgScore,
                    )
                    // If the session came back without HR (Room cache path never stores HR),
                    // fetch it directly using the intraday endpoint which is known to work.
                    if (session != null && session.heartRateSamples.isEmpty()) {
                        launch { augmentSessionHr(session) }
                    }
                }.onFailure {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
            }
        }
        viewModelScope.launch {
            sleepRepository.getAnnotationsFlow().collect { annotations ->
                _uiState.value = _uiState.value.copy(
                    annotation = annotations[sessionId] ?: SleepAnnotation(),
                )
            }
        }
    }

    private suspend fun augmentSessionHr(session: SleepSession) {
        val zone = ZoneId.systemDefault()
        val startDate = session.startTime.atZone(zone).toLocalDate()
        val endDate = session.endTime.atZone(zone).toLocalDate()

        val samples = buildList {
            var date = startDate
            while (!date.isAfter(endDate)) {
                val intradayHr = runCatching {
                    healthConnectManager.readIntradayHrForDate(date, zone)
                }.getOrDefault(emptyList())
                addAll(
                    intradayHr
                        .filter { it.timestamp >= session.startTime && it.timestamp <= session.endTime }
                        .map { it.timestamp to it.bpm.toInt() }
                )
                date = date.plusDays(1)
            }
        }.sortedBy { it.first }

        if (samples.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(
                session = session.copy(heartRateSamples = samples),
            )
        }
    }

    fun saveAnnotation(annotation: SleepAnnotation) {
        viewModelScope.launch {
            sleepRepository.setAnnotation(sessionId, annotation)
        }
    }
}
