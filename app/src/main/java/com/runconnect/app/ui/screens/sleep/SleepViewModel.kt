package com.runconnect.app.ui.screens.sleep

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.runconnect.app.data.preferences.AppPreferences
import com.runconnect.app.data.repository.SleepRepository
import com.runconnect.app.domain.analytics.SleepAnalyticsEngine
import com.runconnect.app.domain.model.BedtimeStats
import com.runconnect.app.domain.model.MonthlySleepReport
import com.runconnect.app.domain.model.SleepAnnotation
import com.runconnect.app.domain.model.SleepCorrelation
import com.runconnect.app.domain.model.SleepDebtInfo
import com.runconnect.app.domain.model.SleepPrediction
import com.runconnect.app.domain.model.SleepRecommendation
import com.runconnect.app.domain.model.SleepScoreResult
import com.runconnect.app.domain.model.SleepSession
import com.runconnect.app.domain.model.WeeklySleepReport
import com.runconnect.app.domain.model.WakeTimeStats
import com.runconnect.app.domain.scoring.SleepScoreCalculator
import com.runconnect.app.ui.components.packageToDisplayName
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class DailyScore(
    val date: LocalDate,
    val score: Int?,
    val totalSleepMinutes: Long?,
    val efficiency: Int?,
    val sessionId: String?,
)

enum class ChartRange(val days: Int) {
    SEVEN_DAYS(7), FOURTEEN_DAYS(14), THIRTY_DAYS(30),
    THREE_MONTHS(90), SIX_MONTHS(180), ONE_YEAR(365)
}

data class SleepUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val sessions: List<SleepSession> = emptyList(),
    val napSessions: List<SleepSession> = emptyList(),
    val mainSessions: List<SleepSession> = emptyList(),
    val sourceFilter: String? = null,
    val availableSources: List<Pair<String, String>> = emptyList(),
    val avgDurationMinutes: Long = 0L,
    val avgDeepMinutes: Long = 0L,
    val avgRemMinutes: Long = 0L,
    val avgEfficiency: Int = 0,
    val avgScore: Int = 0,
    val debtInfo: SleepDebtInfo? = null,
    val bedtimeStats: BedtimeStats? = null,
    val wakeTimeStats: WakeTimeStats? = null,
    val personalSleepNeed: Long = 480L,
    val recommendations: List<SleepRecommendation> = emptyList(),
    val prediction: SleepPrediction? = null,
    val weeklyReports: List<WeeklySleepReport> = emptyList(),
    val monthlyReport: MonthlySleepReport? = null,
    val correlations: List<SleepCorrelation> = emptyList(),
    val annotations: Map<String, SleepAnnotation> = emptyMap(),
    val useImperial: Boolean = false,
    // Date navigation
    val selectedDate: LocalDate = LocalDate.now(),
    val selectedSession: SleepSession? = null,
    val selectedScoreResult: SleepScoreResult? = null,
    val rollingScores: List<DailyScore> = emptyList(),
    val chartRange: ChartRange = ChartRange.SEVEN_DAYS,
    val sessionsByDate: Map<LocalDate, List<SleepSession>> = emptyMap(),
    val availableDates: Set<LocalDate> = emptySet(),
    val sleepTargetMinutes: Int = 480,
)

@HiltViewModel
class SleepViewModel @Inject constructor(
    private val sleepRepository: SleepRepository,
    private val appPreferences: AppPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SleepUiState())
    val uiState: StateFlow<SleepUiState> = _uiState

    private var allSessions: List<SleepSession> = emptyList()

    init {
        viewModelScope.launch {
            appPreferences.useImperial.collect { imperial ->
                _uiState.value = _uiState.value.copy(useImperial = imperial)
            }
        }
        viewModelScope.launch {
            appPreferences.sleepTargetMinutes.collect { target ->
                _uiState.value = _uiState.value.copy(sleepTargetMinutes = target)
            }
        }
        viewModelScope.launch {
            appPreferences.sleepSourceFilter.collect { filter ->
                _uiState.value = _uiState.value.copy(sourceFilter = filter)
                applySourceFilter(allSessions, filter)
            }
        }
        loadSleep()
    }

    fun refresh() = loadSleep()

    fun setSourceFilter(pkg: String?) {
        viewModelScope.launch {
            appPreferences.setSleepSourceFilter(pkg)
        }
    }

    private fun loadSleep() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            sleepRepository.getSleepSessions(daysBack = 90).collectLatest { result ->
                result.onSuccess { sessions ->
                    allSessions = sessions
                    val sources = sessions
                        .map { it.dataOriginPackage }
                        .filter { it.isNotEmpty() }
                        .distinct()
                        .map { it to packageToDisplayName(it) }
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        availableSources = sources,
                    )
                    val filter = appPreferences.sleepSourceFilter.first()
                    applySourceFilter(sessions, filter)
                }.onFailure { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
                }
            }
        }
        viewModelScope.launch {
            combine(
                sleepRepository.getAnnotationsFlow(),
                appPreferences.sleepNeedOverride,
            ) { annotations, needOverride -> annotations to needOverride }
                .collect { (annotations, needOverride) ->
                    _uiState.value = _uiState.value.copy(annotations = annotations)
                    val main = _uiState.value.mainSessions
                    if (main.isNotEmpty()) {
                        val need = if (needOverride > 0) needOverride else SleepAnalyticsEngine.computePersonalSleepNeed(main)
                        applyAnalytics(main, need, annotations)
                    }
                }
        }
    }

    private fun applySourceFilter(sessions: List<SleepSession>, filter: String?) {
        val filtered = if (filter == null) sessions else sessions.filter { it.dataOriginPackage == filter }
        val main = filtered.filterNot { it.isNap }
        val naps = filtered.filter { it.isNap }
        val avgFn = { fn: (SleepSession) -> Long -> if (main.isEmpty()) 0L else main.map(fn).average().toLong() }

        // Build date-indexed map: key = wake-up LocalDate
        val byDate = main.groupBy { s ->
            s.endTime.atZone(ZoneId.systemDefault()).toLocalDate()
        }
        val availDates = byDate.keys.toSet()
        val currentSelected = _uiState.value.selectedDate
        val selectedDate = if (currentSelected in availDates) currentSelected
            else (availDates.maxOrNull() ?: LocalDate.now())
        val selectedSession = byDate[selectedDate]?.maxByOrNull { it.totalSleepMinutes }
        val targetMinutes = _uiState.value.sleepTargetMinutes.toLong()
        val scoreResult = selectedSession?.let { SleepScoreCalculator.calculate(it, main, targetMinutes) }
        val range = _uiState.value.chartRange
        val rollingScores = buildRollingScores(byDate, range, LocalDate.now())

        _uiState.value = _uiState.value.copy(
            sessions = filtered,
            mainSessions = main,
            napSessions = naps,
            avgDurationMinutes = avgFn { it.totalSleepMinutes },
            avgDeepMinutes = avgFn { it.deepSleepMinutes },
            avgRemMinutes = avgFn { it.remSleepMinutes },
            avgEfficiency = if (main.isEmpty()) 0 else main.map { it.sleepEfficiencyPercent }.average().toInt(),
            avgScore = if (main.isEmpty()) 0 else main.map { it.sleepScore }.average().toInt(),
            sessionsByDate = byDate,
            availableDates = availDates,
            selectedDate = selectedDate,
            selectedSession = selectedSession,
            selectedScoreResult = scoreResult,
            rollingScores = rollingScores,
        )

        viewModelScope.launch {
            val needOverride = appPreferences.sleepNeedOverride.first()
            val annotations = _uiState.value.annotations
            val need = if (needOverride > 0) needOverride else SleepAnalyticsEngine.computePersonalSleepNeed(main)
            applyAnalytics(main, need, annotations)
        }
    }

    fun navigateToDate(date: LocalDate) {
        val state = _uiState.value
        val session = state.sessionsByDate[date]?.maxByOrNull { it.totalSleepMinutes }
        val scoreResult = session?.let {
            SleepScoreCalculator.calculate(it, state.mainSessions, state.sleepTargetMinutes.toLong())
        }
        _uiState.value = state.copy(
            selectedDate = date,
            selectedSession = session,
            selectedScoreResult = scoreResult,
        )
    }

    fun navigatePrev() {
        val state = _uiState.value
        state.availableDates.filter { it < state.selectedDate }.maxOrNull()?.let { navigateToDate(it) }
    }

    fun navigateNext() {
        val state = _uiState.value
        state.availableDates.filter { it > state.selectedDate }.minOrNull()?.let { navigateToDate(it) }
    }

    fun setChartRange(range: ChartRange) {
        val state = _uiState.value
        _uiState.value = state.copy(
            chartRange = range,
            rollingScores = buildRollingScores(state.sessionsByDate, range, LocalDate.now()),
        )
    }

    fun selectSessionById(sessionId: String) {
        val state = _uiState.value
        val session = state.mainSessions.firstOrNull { it.id == sessionId } ?: return
        val date = session.endTime.atZone(ZoneId.systemDefault()).toLocalDate()
        navigateToDate(date)
    }

    private fun buildRollingScores(
        byDate: Map<LocalDate, List<SleepSession>>,
        range: ChartRange,
        today: LocalDate,
    ): List<DailyScore> {
        val start = today.minusDays(range.days.toLong() - 1)
        return (0 until range.days).map { offset ->
            val date = start.plusDays(offset.toLong())
            val session = byDate[date]?.maxByOrNull { it.totalSleepMinutes }
            DailyScore(
                date = date,
                score = session?.sleepScore,
                totalSleepMinutes = session?.totalSleepMinutes,
                efficiency = session?.sleepEfficiencyPercent,
                sessionId = session?.id,
            )
        }
    }

    private fun applyAnalytics(main: List<SleepSession>, need: Long, annotations: Map<String, SleepAnnotation>) {
        val debt = SleepAnalyticsEngine.computeDebt(main, need)
        val bedtime = SleepAnalyticsEngine.computeBedtimeStats(main)
        val wake = SleepAnalyticsEngine.computeWakeTimeStats(main)
        val recs = SleepAnalyticsEngine.buildRecommendations(main, debt, bedtime, wake)
        val pred = SleepAnalyticsEngine.buildPrediction(main, debt, bedtime)
        val weekly = (0..3).mapNotNull { SleepAnalyticsEngine.buildWeeklyReport(main, it) }
        val monthly = SleepAnalyticsEngine.buildMonthlyReport(main)
        val corr = SleepAnalyticsEngine.computeCorrelations(main, annotations)

        _uiState.value = _uiState.value.copy(
            personalSleepNeed = need,
            debtInfo = debt,
            bedtimeStats = bedtime,
            wakeTimeStats = wake,
            recommendations = recs,
            prediction = pred,
            weeklyReports = weekly,
            monthlyReport = monthly,
            correlations = corr,
        )
    }
}
