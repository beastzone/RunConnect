package com.runconnect.app.ui.screens.activitydetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.runconnect.app.domain.analytics.ActivityHrAnalytics
import com.runconnect.app.domain.model.Activity
import com.runconnect.app.domain.model.ActivityType
import com.runconnect.app.domain.model.averagePaceSecondsPerKm
import com.runconnect.app.domain.model.averagePaceSecondsPerMile
import com.runconnect.app.domain.model.distanceKm
import com.runconnect.app.domain.model.distanceMiles
import com.runconnect.app.ui.components.ActivityHrChart
import com.runconnect.app.ui.components.ElevationChart
import com.runconnect.app.ui.components.HrZoneBar
import com.runconnect.app.ui.components.PaceChart
import com.runconnect.app.ui.components.SectionHeader
import com.runconnect.app.ui.components.SmallStatItem
import com.runconnect.app.ui.components.accentColor
import com.runconnect.app.ui.components.icon
import com.runconnect.app.ui.components.packageToDisplayName
import com.runconnect.app.ui.theme.AmberAccent
import com.runconnect.app.ui.theme.Background
import com.runconnect.app.ui.theme.CardDark
import com.runconnect.app.ui.theme.CoralAccent
import com.runconnect.app.ui.theme.HeartRate
import com.runconnect.app.ui.theme.TealPrimary
import com.runconnect.app.ui.theme.TextPrimary
import com.runconnect.app.ui.theme.TextSecondary
import com.runconnect.app.utils.FormatUtils
import kotlin.math.abs

@Composable
fun ActivityDetailScreen(
    activityId: String,
    onBack: () -> Unit,
    viewModel: ActivityDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Box(modifier = Modifier.fillMaxSize().background(Background)) {
        when {
            state.isLoading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center).size(36.dp),
                    color = TealPrimary,
                )
            }
            state.error != null -> {
                Text(
                    state.error!!,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            state.activity != null -> {
                ActivityDetailContent(
                    activity = state.activity!!,
                    state = state,
                    onBack = onBack,
                )
            }
        }
    }
}

@Composable
private fun ActivityDetailContent(
    activity: Activity,
    state: ActivityDetailUiState,
    onBack: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        // Header with back button
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(CardDark)
                ) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                }
            }
        }

        // Activity title + date
        item {
            Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(activity.type.accentColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            activity.type.icon,
                            contentDescription = null,
                            tint = activity.type.accentColor,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(modifier = Modifier.padding(start = 10.dp))
                    Column {
                        Text(
                            activity.title,
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary,
                        )
                        val sourceLabel = packageToDisplayName(activity.dataOriginPackage)
                        Text(
                            FormatUtils.formatFullDate(activity.startTime, activity.startZoneOffset) + " · " +
                                    FormatUtils.formatTime(activity.startTime, activity.startZoneOffset) +
                                    if (sourceLabel.isNotEmpty()) " · $sourceLabel" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                    }
                }
            }
        }

        // 3D Mapbox route
        item {
            MapboxRouteView(
                routePoints = activity.route,
                isLoading = state.routeLoading,
                consentRequired = state.routeConsentRequired,
                modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp),
            )
        }

        // Key stats row
        item {
            Box(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 16.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardDark)
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    SmallStatItem(
                        label = if (state.useImperial) "Miles" else "Km",
                        value = if (state.useImperial) "%.2f".format(activity.distanceMiles)
                        else "%.2f".format(activity.distanceKm),
                    )
                    SmallStatItem(
                        label = "Duration",
                        value = FormatUtils.formatDuration(activity.durationSeconds),
                    )
                    if (activity.type == ActivityType.RUNNING || activity.type == ActivityType.HIKING) {
                        val pace = if (state.useImperial) activity.averagePaceSecondsPerMile
                        else activity.averagePaceSecondsPerKm
                        SmallStatItem(
                            label = if (state.useImperial) "Avg /mi" else "Avg /km",
                            value = if (pace > 0) "%d:%02d".format((pace / 60).toInt(), (pace % 60).toInt())
                            else "--",
                        )
                    }
                    if (activity.elevationGainMeters > 0) {
                        SmallStatItem(
                            label = "Elev Gain",
                            value = FormatUtils.formatElevation(activity.elevationGainMeters, state.useImperial),
                        )
                    }
                    activity.averageHeartRate?.let { hr ->
                        SmallStatItem(label = "Avg HR", value = "$hr bpm", valueColor = HeartRate)
                    }
                }
            }
        }

        // Elevation profile
        if (activity.route.any { it.altitudeMeters != null }) {
            item {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 16.dp)
                ) {
                    SectionHeader("Elevation Profile")
                    Spacer(Modifier.height(10.dp))
                    ElevationChart(
                        routePoints = activity.route,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        // Pace chart
        if (state.paceChartData.size >= 2) {
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
                    SectionHeader("Pace")
                    Spacer(Modifier.height(10.dp))
                    PaceChart(
                        paceData = state.paceChartData,
                        avgPaceSeconds = activity.averagePaceSecondsPerKm,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        // Enhanced heart rate chart
        if (state.hrChartData.size >= 2) {
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
                    SectionHeader("Heart Rate")
                    Spacer(Modifier.height(10.dp))
                    ActivityHrChart(
                        hrData = state.hrChartData,
                        avgHr = activity.averageHeartRate ?: 0,
                        maxHrSetting = state.maxHrSetting,
                        activityStartEpoch = activity.startTime.epochSecond,
                        activityDurationSeconds = activity.durationSeconds,
                        laps = activity.laps,
                        zoneModel = state.zoneModel,
                        restingHr = if (state.restingHrOverride > 0) state.restingHrOverride else 60,
                        artifacts = state.hrArtifacts,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        // Artifact warning banner
        if (state.hrArtifacts.suspectTimestamps.isNotEmpty()) {
            item {
                ArtifactWarningBanner(
                    artifacts = state.hrArtifacts,
                    modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp),
                )
            }
        }

        // HR Zones card
        if (state.hrZoneDistribution != null && state.hrZoneDistribution.totalSamples > 0) {
            item {
                HrZonesCard(
                    distribution = state.hrZoneDistribution,
                    modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp),
                )
            }
        }

        // HR Drift card (only when significant)
        if (state.hrDrift != null && state.hrDrift.isSignificant) {
            item {
                HrDriftCard(
                    drift = state.hrDrift,
                    modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp),
                )
            }
        }

        // Aerobic Decoupling card (requires ≥ 20 min activity)
        if (state.aerobicDecoupling != null && activity.durationSeconds >= 1200) {
            item {
                AerobicDecouplingCard(
                    decoupling = state.aerobicDecoupling,
                    modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp),
                )
            }
        }

        // HR Recovery card
        if (state.hrRecovery != null && (state.hrRecovery.drop1Min != null || state.hrRecovery.drop2Min != null)) {
            item {
                HrRecoveryCard(
                    recovery = state.hrRecovery,
                    modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp),
                )
            }
        }

        // Race predictions (only for runs)
        if (activity.type == ActivityType.RUNNING && state.racePredictions.isNotEmpty()) {
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
                    RacePredictionsCard(predictions = state.racePredictions)
                }
            }
        }

        // Lap splits
        if (activity.laps.isNotEmpty()) {
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
                    LapSplitsCard(laps = activity.laps, useImperial = state.useImperial)
                }
            }
        }
    }
}

@Composable
private fun ArtifactWarningBanner(
    artifacts: ActivityHrAnalytics.ArtifactSpans,
    modifier: Modifier = Modifier,
) {
    val count = artifacts.suspectTimestamps.size
    val types = artifacts.reasons.values.toSet().joinToString(", ") { reason ->
        when (reason) {
            "spike" -> "spikes"
            "flatline" -> "flatlines"
            "dropout" -> "dropouts"
            "cadence_lock" -> "cadence lock"
            else -> reason
        }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AmberAccent.copy(alpha = 0.12f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = AmberAccent, modifier = Modifier.size(18.dp))
        Column {
            Text(
                "$count possible sensor artifact${if (count == 1) "" else "s"} in HR data",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = AmberAccent,
            )
            if (types.isNotEmpty()) {
                Text(types, style = MaterialTheme.typography.labelSmall, color = AmberAccent.copy(alpha = 0.8f))
            }
        }
    }
}

@Composable
private fun HrZonesCard(
    distribution: ActivityHrAnalytics.ZoneDistribution,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardDark)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "Heart Rate Zones",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = TextPrimary,
        )
        distribution.summaries.forEach { summary ->
            HrZoneBar(summary = summary, showTime = true)
        }
    }
}

@Composable
private fun HrDriftCard(
    drift: ActivityHrAnalytics.HrDriftResult,
    modifier: Modifier = Modifier,
) {
    val driftColor = when {
        abs(drift.driftPercent) <= 5.0 -> TealPrimary
        abs(drift.driftPercent) <= 10.0 -> AmberAccent
        else -> CoralAccent
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardDark)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Heart Rate Drift",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = TextPrimary,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SmallStatItem(
                label = "1st half avg",
                value = "${drift.firstHalfAvgBpm.toInt()} bpm",
                modifier = Modifier.weight(1f),
            )
            SmallStatItem(
                label = "2nd half avg",
                value = "${drift.secondHalfAvgBpm.toInt()} bpm",
                modifier = Modifier.weight(1f),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text("Drift", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                val sign = if (drift.driftPercent > 0) "+" else ""
                Text(
                    "$sign${"%.1f".format(drift.driftPercent)}%",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = driftColor,
                )
            }
        }
        Text(
            "HR drift suggests possible fatigue, heat, or dehydration. Multiple factors may contribute.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )
    }
}

@Composable
private fun AerobicDecouplingCard(
    decoupling: ActivityHrAnalytics.AerobicDecouplingResult,
    modifier: Modifier = Modifier,
) {
    val couplingColor = if (decoupling.isWellCoupled) TealPrimary else CoralAccent
    val couplingLabel = if (decoupling.isWellCoupled) "Well coupled" else "Decoupled"

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardDark)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Aerobic Decoupling (Pa:Hr)",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = TextPrimary,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Decoupling", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                Text(
                    "${"%.1f".format(decoupling.decouplingPercent)}%",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = couplingColor,
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(couplingColor.copy(alpha = 0.15f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(couplingLabel, style = MaterialTheme.typography.labelMedium, color = couplingColor)
            }
        }
        decoupling.historicalDecouplingAvg?.let { histAvg ->
            val delta = decoupling.decouplingPercent - histAvg
            val sign = if (delta > 0) "+" else ""
            Text(
                "Your avg: ${"%.1f".format(histAvg)}%  ($sign${"%.1f".format(delta)}% today)",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }
        Text(
            "< 5% suggests your aerobic fitness matches today's effort.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )
    }
}

@Composable
private fun HrRecoveryCard(
    recovery: ActivityHrAnalytics.HrRecoveryResult,
    modifier: Modifier = Modifier,
) {
    fun dropColor(drop: Int?, threshold1: Int, threshold2: Int) = when {
        drop == null -> TextSecondary
        drop >= threshold2 -> TealPrimary
        drop >= threshold1 -> AmberAccent
        else -> TextSecondary
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardDark)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "Heart Rate Recovery",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = TextPrimary,
        )
        Text(
            "Peak: ${recovery.peakBpm} bpm",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            listOf(
                Triple("1 min", recovery.drop1Min, 15 to 20),
                Triple("2 min", recovery.drop2Min, 22 to 30),
                Triple("5 min", recovery.drop5Min, 40 to 50),
            ).forEach { (label, drop, thresholds) ->
                Column(modifier = Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Text(
                        if (drop != null) "−$drop bpm" else "--",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = dropColor(drop, thresholds.first, thresholds.second),
                    )
                }
            }
        }
    }
}
