package com.runconnect.app.ui.screens.activitydetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.runconnect.app.data.preferences.AppPreferences
import com.runconnect.app.data.repository.ActivityRepository
import com.runconnect.app.domain.analytics.ActivityHrAnalytics
import com.runconnect.app.domain.model.Activity
import com.runconnect.app.domain.model.RacePrediction
import com.runconnect.app.domain.model.RoutePoint
import com.runconnect.app.domain.model.SpeedSample
import com.runconnect.app.domain.model.ZoneModel
import com.runconnect.app.utils.RacePredictionCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs
import javax.inject.Inject

enum class TerrainFilter { ALL, FLAT, UPHILL, DOWNHILL }

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
    // Scatter plots + terrain filter (Feature 8.6/8.7)
    val hrVsPacePoints: List<Pair<Float, Float>> = emptyList(),
    val hrVsElevationPoints: List<Pair<Float, Float>> = emptyList(),
    val terrainFilter: TerrainFilter = TerrainFilter.ALL,
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

    // Cached per-terrain HR-vs-pace lists; populated after route loads (terrain available)
    private var hrPaceByTerrain: Map<TerrainFilter, List<Pair<Float, Float>>> = emptyMap()

    init {
        loadActivity()
    }

    fun setTerrainFilter(filter: TerrainFilter) {
        val points = hrPaceByTerrain[filter]
            ?: if (filter == TerrainFilter.ALL) emptyList()
            else hrPaceByTerrain[TerrainFilter.ALL] ?: emptyList()
        _uiState.value = _uiState.value.copy(terrainFilter = filter, hrVsPacePoints = points)
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

            // HR vs Pace (all terrain — no route needed)
            val hrVsPaceAll = buildHrVsPacePoints(activity.heartRateSamples, activity.speedSamples)
            hrPaceByTerrain = mapOf(TerrainFilter.ALL to hrVsPaceAll)

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
                hrVsPacePoints = hrVsPaceAll,
            )

            val (activityWithRoute, consentRequired) = runCatching {
                activityRepository.getActivityWithRoute(activityId)
            }.getOrDefault(null to false)

            if (activityWithRoute != null && activityWithRoute.route.isNotEmpty()) {
                // Recompute terrain-classified scatter after route is available
                val terrainMap = buildTerrainClassifiedPacePoints(
                    activity.heartRateSamples, activity.speedSamples, activityWithRoute.route,
                )
                hrPaceByTerrain = terrainMap

                val elevPoints = buildHrVsElevationPoints(activity.heartRateSamples, activityWithRoute.route)
                val currentFilter = _uiState.value.terrainFilter

                _uiState.value = _uiState.value.copy(
                    activity = activityWithRoute,
                    routeLoading = false,
                    routeConsentRequired = false,
                    hrVsPacePoints = terrainMap[currentFilter] ?: terrainMap[TerrainFilter.ALL] ?: hrVsPaceAll,
                    hrVsElevationPoints = elevPoints,
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

    private fun buildHrVsPacePoints(
        hrSamples: List<com.runconnect.app.domain.model.HeartRateSample>,
        speedSamples: List<SpeedSample>,
    ): List<Pair<Float, Float>> {
        if (hrSamples.isEmpty() || speedSamples.isEmpty()) return emptyList()
        return hrSamples.mapNotNull { hr ->
            val closest = speedSamples.minByOrNull { abs(it.timestamp.epochSecond - hr.timestamp.epochSecond) }
                ?: return@mapNotNull null
            if (abs(closest.timestamp.epochSecond - hr.timestamp.epochSecond) > 15) return@mapNotNull null
            if (closest.speedMps < 0.5) return@mapNotNull null
            val paceSecPerKm = (1000.0 / closest.speedMps).toFloat()
            if (paceSecPerKm !in 60f..1800f) return@mapNotNull null
            paceSecPerKm to hr.bpm.toFloat()
        }
    }

    private fun buildTerrainClassifiedPacePoints(
        hrSamples: List<com.runconnect.app.domain.model.HeartRateSample>,
        speedSamples: List<SpeedSample>,
        route: List<RoutePoint>,
    ): Map<TerrainFilter, List<Pair<Float, Float>>> {
        val routeWithAlt = route.filter { it.altitudeMeters != null }.sortedBy { it.timestamp.epochSecond }
        if (routeWithAlt.size < 2) {
            val all = buildHrVsPacePoints(hrSamples, speedSamples)
            return mapOf(TerrainFilter.ALL to all)
        }

        data class TaggedPoint(val paceSecPerKm: Float, val bpm: Float, val terrain: TerrainFilter)

        val tagged = hrSamples.mapNotNull { hr ->
            val closestSpeed = speedSamples.minByOrNull { abs(it.timestamp.epochSecond - hr.timestamp.epochSecond) }
                ?: return@mapNotNull null
            if (abs(closestSpeed.timestamp.epochSecond - hr.timestamp.epochSecond) > 15) return@mapNotNull null
            if (closestSpeed.speedMps < 0.5) return@mapNotNull null
            val paceSecPerKm = (1000.0 / closestSpeed.speedMps).toFloat()
            if (paceSecPerKm !in 60f..1800f) return@mapNotNull null

            val epoch = hr.timestamp.epochSecond
            val terrain = classifyTerrain(epoch, routeWithAlt)
            TaggedPoint(paceSecPerKm, hr.bpm.toFloat(), terrain)
        }

        val allPoints = tagged.map { it.paceSecPerKm to it.bpm }
        return mapOf(
            TerrainFilter.ALL to allPoints,
            TerrainFilter.FLAT to tagged.filter { it.terrain == TerrainFilter.FLAT }.map { it.paceSecPerKm to it.bpm },
            TerrainFilter.UPHILL to tagged.filter { it.terrain == TerrainFilter.UPHILL }.map { it.paceSecPerKm to it.bpm },
            TerrainFilter.DOWNHILL to tagged.filter { it.terrain == TerrainFilter.DOWNHILL }.map { it.paceSecPerKm to it.bpm },
        )
    }

    private fun classifyTerrain(epoch: Long, routeWithAlt: List<RoutePoint>): TerrainFilter {
        val windowSec = 30L
        val before = routeWithAlt.filter { it.timestamp.epochSecond in (epoch - windowSec)..epoch }
        val after = routeWithAlt.filter { it.timestamp.epochSecond in epoch..(epoch + windowSec) }
        val altBefore = before.lastOrNull()?.altitudeMeters ?: return TerrainFilter.FLAT
        val altAfter = after.firstOrNull()?.altitudeMeters ?: return TerrainFilter.FLAT
        val gain = altAfter - altBefore
        return when {
            gain > 5.0 -> TerrainFilter.UPHILL
            gain < -5.0 -> TerrainFilter.DOWNHILL
            else -> TerrainFilter.FLAT
        }
    }

    private fun buildHrVsElevationPoints(
        hrSamples: List<com.runconnect.app.domain.model.HeartRateSample>,
        route: List<RoutePoint>,
    ): List<Pair<Float, Float>> {
        val routeWithAlt = route.filter { it.altitudeMeters != null }.sortedBy { it.timestamp.epochSecond }
        if (routeWithAlt.isEmpty()) return emptyList()
        return hrSamples.mapNotNull { hr ->
            val closest = routeWithAlt.minByOrNull { abs(it.timestamp.epochSecond - hr.timestamp.epochSecond) }
                ?: return@mapNotNull null
            if (abs(closest.timestamp.epochSecond - hr.timestamp.epochSecond) > 15) return@mapNotNull null
            val alt = closest.altitudeMeters!!.toFloat()
            alt to hr.bpm.toFloat()
        }
    }
}
