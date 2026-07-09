package com.runconnect.app.ui.screens.activitydetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.runconnect.app.data.preferences.AppPreferences
import com.runconnect.app.data.repository.ActivityRepository
import com.runconnect.app.domain.analytics.ActivityHrAnalytics
import com.runconnect.app.domain.model.Activity
import com.runconnect.app.domain.model.RacePrediction
import com.runconnect.app.domain.model.SpeedSample
import com.runconnect.app.domain.model.ZoneModel
import com.runconnect.app.utils.RacePredictionCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ActivityDetailUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val activity: Activity? = null,
    val racePredictions: List<RacePrediction> = emptyList(),
    val paceChartData: List<Pair<Float, Float>> = emptyList(),
    val hrChartData: List<Pair<Float, Int>> = emptyList(),
    val useImperial: Boolean = false,
    val routeLoading: Boolean = false,
    val routeConsentRequired: Boolean = false,
    // HR analytics (Feature 8)
    val hrZoneDistribution: ActivityHrAnalytics.ZoneDistribution? = null,
    val hrDrift: ActivityHrAnalytics.HrDriftResult? = null,
    val aerobicDecoupling: ActivityHrAnalytics.AerobicDecouplingResult? = null,
    val hrRecovery: ActivityHrAnalytics.HrRecoveryResult? = null,
    val hrArtifacts: ActivityHrAnalytics.ArtifactSpans = ActivityHrAnalytics.ArtifactSpans.empty,
    val hrEfficiency: Double? = null,
    val zoneModel: ZoneModel = ZoneModel.MAX_HR,
    val maxHrSetting: Int = 190,
    val restingHrOverride: Int = 0,
)

@HiltViewModel
class ActivityDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val activityRepository: ActivityRepository,
    private val appPreferences: AppPreferences,
) : ViewModel() {

    private val activityId: String = savedStateHandle["activityId"] ?: ""

    private val _uiState = MutableStateFlow(ActivityDetailUiState())
    val uiState: StateFlow<ActivityDetailUiState> = _uiState

    init {
        loadActivity()
    }

    private fun loadActivity() {
        viewModelScope.launch {
            val useImperial = appPreferences.useImperial.first()
            val maxHr = appPreferences.maxHeartRate.first()
            val zoneModel = appPreferences.zoneModel.first()
            val restingHrOverride = appPreferences.restingHrOverride.first()
            val effectiveRestingHr = if (restingHrOverride > 0) restingHrOverride else 60

            _uiState.value = _uiState.value.copy(
                isLoading = true,
                useImperial = useImperial,
                maxHrSetting = maxHr,
                zoneModel = zoneModel,
                restingHrOverride = restingHrOverride,
            )

            val activity = activityRepository.getActivityById(activityId)
            if (activity == null) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Activity not found")
                return@launch
            }

            val predictions = RacePredictionCalculator.predict(activity)
            val paceData = buildPaceChartData(activity.speedSamples, activity.startTime.epochSecond)
            val hrData = buildHrChartData(activity.heartRateSamples, activity.startTime.epochSecond)

            // Compute HR analytics if samples are available
            var zoneDistribution: ActivityHrAnalytics.ZoneDistribution? = null
            var hrDrift: ActivityHrAnalytics.HrDriftResult? = null
            var aerobicDecoupling: ActivityHrAnalytics.AerobicDecouplingResult? = null
            var hrRecovery: ActivityHrAnalytics.HrRecoveryResult? = null
            var hrArtifacts = ActivityHrAnalytics.ArtifactSpans.empty
            var hrEfficiency: Double? = null

            if (activity.heartRateSamples.isNotEmpty()) {
                zoneDistribution = runCatching {
                    ActivityHrAnalytics.computeZones(activity.heartRateSamples, maxHr, zoneModel, effectiveRestingHr)
                }.getOrNull()

                hrDrift = runCatching {
                    ActivityHrAnalytics.computeHrDrift(activity.heartRateSamples, activity.durationSeconds)
                }.getOrNull()

                val historicalDecouplingAvg = runCatching {
                    activityRepository.getActivities(90).first()
                        .getOrDefault(emptyList())
                        .filter { it.type == activity.type && it.id != activity.id }
                        .takeLast(10)
                        .mapNotNull { a ->
                            ActivityHrAnalytics.computeAerobicDecoupling(
                                a.heartRateSamples, a.speedSamples, a.durationSeconds
                            )?.decouplingPercent
                        }
                        .takeIf { it.size >= 3 }
                        ?.sorted()
                        ?.let { sorted -> sorted[sorted.size / 2] }
                }.getOrNull()

                aerobicDecoupling = runCatching {
                    ActivityHrAnalytics.computeAerobicDecoupling(
                        activity.heartRateSamples,
                        activity.speedSamples,
                        activity.durationSeconds,
                        historicalDecouplingAvg,
                    )
                }.getOrNull()

                hrRecovery = runCatching {
                    ActivityHrAnalytics.computeEndOfActivityRecovery(activity.heartRateSamples, activity.endTime)
                }.getOrNull()

                hrArtifacts = runCatching {
                    ActivityHrAnalytics.detectArtifacts(activity.heartRateSamples)
                }.getOrDefault(ActivityHrAnalytics.ArtifactSpans.empty)

                hrEfficiency = runCatching {
                    ActivityHrAnalytics.computeEfficiency(activity.heartRateSamples, activity.speedSamples)
                }.getOrNull()
            }

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                activity = activity,
                racePredictions = predictions,
                paceChartData = paceData,
                hrChartData = hrData,
                useImperial = useImperial,
                routeLoading = true,
                hrZoneDistribution = zoneDistribution,
                hrDrift = hrDrift,
                aerobicDecoupling = aerobicDecoupling,
                hrRecovery = hrRecovery,
                hrArtifacts = hrArtifacts,
                hrEfficiency = hrEfficiency,
            )

            val (activityWithRoute, consentRequired) = runCatching {
                activityRepository.getActivityWithRoute(activityId)
            }.getOrDefault(null to false)

            if (activityWithRoute != null && activityWithRoute.route.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(
                    activity = activityWithRoute,
                    routeLoading = false,
                    routeConsentRequired = false,
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    routeLoading = false,
                    routeConsentRequired = consentRequired,
                )
            }
        }
    }

    private fun buildPaceChartData(
        samples: List<SpeedSample>,
        startEpochSeconds: Long,
    ): List<Pair<Float, Float>> =
        samples.mapNotNull { sample ->
            val t = (sample.timestamp.epochSecond - startEpochSeconds).toFloat()
            val paceSecPerKm = if (sample.speedMps > 0.5) (1000.0 / sample.speedMps).toFloat() else null
            paceSecPerKm?.let { t to it }
        }.filter { it.second in 60f..1800f }

    private fun buildHrChartData(
        samples: List<com.runconnect.app.domain.model.HeartRateSample>,
        startEpochSeconds: Long,
    ): List<Pair<Float, Int>> =
        samples.map { sample ->
            val t = (sample.timestamp.epochSecond - startEpochSeconds).toFloat()
            t to sample.bpm.toInt()
        }
}
