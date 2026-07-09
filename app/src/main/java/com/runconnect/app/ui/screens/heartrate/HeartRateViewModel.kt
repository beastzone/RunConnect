package com.runconnect.app.ui.screens.heartrate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.runconnect.app.data.healthconnect.HealthConnectManager
import com.runconnect.app.data.preferences.AppPreferences
import com.runconnect.app.data.repository.ActivityRepository
import com.runconnect.app.domain.analytics.ActivityHrAnalytics
import com.runconnect.app.domain.model.ActivityType
import com.runconnect.app.domain.model.ElevatedRhrAlert
import com.runconnect.app.domain.model.HeartRateSample
import com.runconnect.app.domain.model.HeartRateZoneSummary
import com.runconnect.app.domain.model.HrByTypeStats
import com.runconnect.app.domain.model.HrZone
import com.runconnect.app.domain.model.LowRhrAlert
import com.runconnect.app.domain.model.RhrRollingAvgs
import com.runconnect.app.domain.model.WorkoutRecoveryPoint
import com.runconnect.app.domain.model.ZoneModel
import com.runconnect.app.domain.model.computeHrZone
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
import kotlin.math.sqrt

data class HeartRateUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    // Basic stats
    val avgRestingHr: Int = 0,
    val maxRecordedHr: Int = 0,
    val zoneSummaries: List<HeartRateZoneSummary> = emptyList(),
    val restingHrHistory: List<Pair<Long, Int>> = emptyList(),
    val hrvHistory: List<Pair<Long, Double>> = emptyList(),
    val maxHrSetting: Int = 190,
    val zoneModel: ZoneModel = ZoneModel.MAX_HR,
    val restingHrOverride: Int = 0,
    // 12.3 enhanced RHR stats
    val rhrSevenDayAvg: Int? = null,
    val rhrThirtyDayBaseline: Int? = null,
    val rhrDeltaFromBaseline: Int? = null,
    // 12.10 Rolling averages
    val rhrRollingAvgs: RhrRollingAvgs? = null,
    // 12.11 Baseline deviation (std deviations from personal mean)
    val rhrBaselineDeviation: Float? = null,
    // 12.12/12.13 Alerts
    val elevatedRhrAlert: ElevatedRhrAlert? = null,
    val lowRhrAlert: LowRhrAlert? = null,
    // 12.14 Recovery trends (1-min drop after workouts)
    val recoveryTrends: List<WorkoutRecoveryPoint> = emptyList(),
    // 12.16 HR by activity type
    val hrByActivityType: Map<ActivityType, HrByTypeStats> = emptyMap(),
    // 12.2 / 12.1 Today's HR
    val todayHrSamples: List<HeartRateSample> = emptyList(),
    val currentHrBpm: Int? = null,
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
            val zoneModel = appPreferences.zoneModel.first()
            val restingHrOverride = appPreferences.restingHrOverride.first()
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                maxHrSetting = maxHr,
                zoneModel = zoneModel,
                restingHrOverride = restingHrOverride,
            )

            // Load dedicated resting HR + HRV + intraday HR in parallel
            val restingHrDeferred = async {
                runCatching { healthConnectManager.readRestingHeartRateHistory(90) }.getOrDefault(emptyList())
            }
            val hrvDeferred = async {
                runCatching { healthConnectManager.readHrvHistory(30) }.getOrDefault(emptyList())
            }
            val todayHrDeferred = async {
                runCatching {
                    healthConnectManager.readIntradayHrForDate(LocalDate.now(), ZoneId.systemDefault())
                }.getOrDefault(emptyList())
            }

            activityRepository.getActivities(daysBack = 90).collectLatest { result ->
                val rhrHistory90 = restingHrDeferred.await()
                val hrvRaw = hrvDeferred.await()
                val todayHr = todayHrDeferred.await()

                val avgRhrFromHistory = if (rhrHistory90.isNotEmpty())
                    rhrHistory90.map { it.second }.average().toInt() else 0
                val effectiveRestingHr = if (restingHrOverride > 0) restingHrOverride
                    else if (avgRhrFromHistory > 0) avgRhrFromHistory else 60

                result.onSuccess { activities ->
                    // Zone distribution (from last 30 days of activities)
                    val zoneMinutes = mutableMapOf<HrZone, Long>()
                    activities.takeLast(60).forEach { activity ->
                        activity.heartRateSamples.forEach { sample ->
                            val zone = computeHrZone(sample.bpm.toInt(), maxHr, zoneModel, effectiveRestingHr)
                            zoneMinutes[zone] = (zoneMinutes[zone] ?: 0L) + 1
                        }
                    }
                    val totalMinutes = zoneMinutes.values.sum().coerceAtLeast(1L)
                    val zoneSummaries = HrZone.entries.map { zone ->
                        val mins = zoneMinutes[zone] ?: 0L
                        HeartRateZoneSummary(zone, mins, mins.toFloat() / totalMinutes)
                    }

                    val allBpms = activities.flatMap { it.heartRateSamples }.map { it.bpm.toInt() }

                    // RHR sorted by date ascending (for chart)
                    val rhrHistoryForChart = rhrHistory90
                        .sortedBy { it.first }
                        .map { it.first.epochSecond to it.second }

                    // Enhanced RHR stats from history
                    val rhrValues90 = rhrHistory90.sortedByDescending { it.first }.map { it.second }
                    val rhr7d = if (rhrValues90.size >= 3) rhrValues90.take(7).average().toInt() else null
                    val rhr30d = if (rhrValues90.size >= 7)
                        rhrValues90.take(30).let { v -> v.sorted()[v.size / 2] } else null

                    val delta = if (rhr7d != null && rhr30d != null) rhr7d - rhr30d else null

                    // Rolling averages
                    fun avg(n: Int) = rhrValues90.take(n).takeIf { it.size >= 3 }?.average()?.toInt()
                    val rolling = if (rhrValues90.size >= 3) RhrRollingAvgs(
                        d7 = avg(7), d14 = avg(14), d30 = avg(30), d90 = avg(90)
                    ) else null

                    // Baseline deviation
                    val deviation = if (rhrValues90.size >= 14 && rhr7d != null) {
                        val mean = rhrValues90.average()
                        val variance = rhrValues90.map { (it - mean) * (it - mean) }.average()
                        val stddev = sqrt(variance).takeIf { it > 0 } ?: 1.0
                        ((rhr7d - mean) / stddev).toFloat()
                    } else null

                    // Elevated RHR alert (12.12)
                    val elevated = if (rhr7d != null && rhr30d != null && rhr7d > rhr30d + 5) {
                        ElevatedRhrAlert(
                            currentAvg = rhr7d,
                            baseline = rhr30d,
                            possibleCauses = listOf(
                                "Poor sleep quality",
                                "Recent hard training",
                                "Heat or dehydration",
                                "Stress",
                                "Illness",
                                "Alcohol",
                                "Incomplete data",
                            ),
                        )
                    } else null

                    // Low RHR alert (12.13) — athletic context considered
                    val low = if (rhr7d != null && rhr30d != null && rhr7d < rhr30d - 8 && rhr7d < 45) {
                        LowRhrAlert(currentAvg = rhr7d, baseline = rhr30d)
                    } else null

                    // Recovery trends (12.14) — last 30 activities with HR samples
                    val recoveryTrends = activities
                        .filter { it.heartRateSamples.isNotEmpty() }
                        .takeLast(30)
                        .mapNotNull { activity ->
                            val recovery = runCatching {
                                ActivityHrAnalytics.computeEndOfActivityRecovery(
                                    activity.heartRateSamples, activity.endTime,
                                )
                            }.getOrNull() ?: return@mapNotNull null
                            val date = activity.startTime
                                .atZone(activity.startZoneOffset ?: ZoneId.systemDefault())
                                .toLocalDate()
                            WorkoutRecoveryPoint(
                                date = date,
                                drop1Min = recovery.drop1Min,
                                drop5Min = recovery.drop5Min,
                            )
                        }
                        .filter { it.drop1Min != null || it.drop5Min != null }

                    // HR by activity type (12.16)
                    val hrByType = activities
                        .filter { it.heartRateSamples.isNotEmpty() }
                        .groupBy { it.type }
                        .mapValues { (_, acts) ->
                            val hrSamples = acts.flatMap { it.heartRateSamples }.map { it.bpm.toInt() }
                            HrByTypeStats(
                                avgHr = hrSamples.average().toInt(),
                                maxHr = hrSamples.max(),
                                activityCount = acts.size,
                            )
                        }

                    // Today's HR
                    val currentBpm = todayHr.lastOrNull()?.bpm?.toInt()

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        avgRestingHr = if (avgRhrFromHistory > 0) avgRhrFromHistory
                            else if (allBpms.isNotEmpty()) allBpms.min() else 0,
                        maxRecordedHr = if (allBpms.isNotEmpty()) allBpms.max() else 0,
                        zoneSummaries = zoneSummaries,
                        restingHrHistory = rhrHistoryForChart.takeLast(90),
                        hrvHistory = hrvRaw.sortedBy { it.first }.takeLast(30).map { it.first.epochSecond to it.second },
                        rhrSevenDayAvg = rhr7d,
                        rhrThirtyDayBaseline = rhr30d,
                        rhrDeltaFromBaseline = delta,
                        rhrRollingAvgs = rolling,
                        rhrBaselineDeviation = deviation,
                        elevatedRhrAlert = elevated,
                        lowRhrAlert = low,
                        recoveryTrends = recoveryTrends,
                        hrByActivityType = hrByType,
                        todayHrSamples = todayHr,
                        currentHrBpm = currentBpm,
                    )
                }.onFailure { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
                }
            }
        }
    }
}
