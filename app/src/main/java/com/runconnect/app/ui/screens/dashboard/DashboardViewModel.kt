package com.runconnect.app.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.runconnect.app.data.preferences.AppPreferences
import com.runconnect.app.data.repository.ActivityRepository
import com.runconnect.app.data.repository.DailyStatsRepository
import com.runconnect.app.data.repository.SleepRepository
import com.runconnect.app.domain.InsightsEngine
import com.runconnect.app.domain.model.Activity
import com.runconnect.app.domain.model.DailyStats
import com.runconnect.app.domain.model.HealthInsight
import com.runconnect.app.domain.model.SleepSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
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
    // Health score (0–100)
    val overallScore: Int = 0,
    val sleepScore: Int = 0,
    val activityScore: Int = 0,
    val recoveryScore: Int = 0,
    // Today's metrics
    val todaySteps: Long = 0L,
    val todayActiveCalories: Double = 0.0,
    val lastNightSleepMinutes: Long = 0L,
    val todayRestingHr: Int? = null,
    val todayHrv: Double? = null,
    // Insights
    val insights: List<HealthInsight> = emptyList(),
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val activityRepository: ActivityRepository,
    private val sleepRepository: SleepRepository,
    private val dailyStatsRepository: DailyStatsRepository,
    private val insightsEngine: InsightsEngine,
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

            // Load activities via flow (respects cache)
            activityRepository.getActivities(daysBack = 90, forceRefresh = forceRefresh)
                .collectLatest { result ->
                    result.onSuccess { activities ->
                        val weekStart = LocalDate.now().minusDays(7)
                            .atStartOfDay(ZoneId.systemDefault()).toInstant()
                        val weekActivities = activities.filter { it.startTime.isAfter(weekStart) }

                        // Load sleep + daily stats in parallel
                        val sleepDeferred = async {
                            runCatching {
                                sleepRepository.getSleepSessions(30).first().getOrDefault(emptyList())
                            }.getOrDefault(emptyList())
                        }
                        val dailyStatsDeferred = async {
                            runCatching { dailyStatsRepository.getDailyStats(30) }.getOrDefault(emptyList())
                        }
                        val sleepSessions = sleepDeferred.await()
                        val dailyStats = dailyStatsDeferred.await()

                        val today = dailyStats.firstOrNull()
                        val lastNight = sleepSessions.firstOrNull()

                        val sleepScore = computeSleepScore(lastNight)
                        val activityScore = computeActivityScore(weekActivities.size)
                        val recoveryScore = computeRecoveryScore(dailyStats)
                        val overallScore = ((sleepScore * 0.35 + activityScore * 0.40 + recoveryScore * 0.25)).toInt()

                        val insights = insightsEngine.generateInsights(activities, sleepSessions, dailyStats)

                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            recentActivities = activities.take(5),
                            weeklyDistanceKm = weekActivities.sumOf { it.distanceMeters } / 1000.0,
                            weeklyActivityCount = weekActivities.size,
                            weeklyTimeSeconds = weekActivities.sumOf { it.durationSeconds },
                            streakDays = computeStreak(activities),
                            overallScore = overallScore,
                            sleepScore = sleepScore,
                            activityScore = activityScore,
                            recoveryScore = recoveryScore,
                            todaySteps = today?.steps ?: 0L,
                            todayActiveCalories = today?.activeCaloriesKcal ?: 0.0,
                            lastNightSleepMinutes = lastNight?.totalDurationMinutes ?: 0L,
                            todayRestingHr = today?.restingHeartRate,
                            todayHrv = today?.hrvRmssd,
                            insights = insights,
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

    private fun computeSleepScore(lastNight: SleepSession?): Int {
        val minutes = lastNight?.totalDurationMinutes ?: return 70 // default when no data
        return when {
            minutes < 240 -> 20
            minutes < 300 -> 35
            minutes < 360 -> 50
            minutes < 420 -> 65
            minutes < 480 -> 85
            minutes < 540 -> 100
            else -> 80
        }
    }

    private fun computeActivityScore(weeklyCount: Int): Int = when {
        weeklyCount == 0 -> 20
        weeklyCount == 1 -> 40
        weeklyCount == 2 -> 60
        weeklyCount == 3 -> 75
        weeklyCount == 4 -> 85
        weeklyCount == 5 -> 93
        else -> 100
    }

    private fun computeRecoveryScore(dailyStats: List<DailyStats>): Int {
        val withRhr = dailyStats.mapNotNull { it.restingHeartRate }
        if (withRhr.size < 3) return 70 // not enough data
        val recentAvg = withRhr.take(3).average()
        val baselineAvg = withRhr.average()
        val delta = recentAvg - baselineAvg
        return when {
            delta > 10 -> 30
            delta > 5 -> 55
            delta > 0 -> 75
            delta > -5 -> 90
            else -> 100
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
