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
import com.runconnect.app.domain.model.Activity
import com.runconnect.app.domain.model.ActivityType
import com.runconnect.app.domain.model.averagePaceSecondsPerKm
import com.runconnect.app.domain.model.averagePaceSecondsPerMile
import com.runconnect.app.domain.model.distanceKm
import com.runconnect.app.domain.model.distanceMiles
import com.runconnect.app.ui.components.ElevationChart
import com.runconnect.app.ui.components.HeartRateChart
import com.runconnect.app.ui.components.PaceChart
import com.runconnect.app.ui.components.SectionHeader
import com.runconnect.app.ui.components.SmallStatItem
import com.runconnect.app.ui.components.accentColor
import com.runconnect.app.ui.components.icon
import com.runconnect.app.ui.theme.Background
import com.runconnect.app.ui.theme.CardDark
import com.runconnect.app.ui.theme.CoralAccent
import com.runconnect.app.ui.theme.HeartRate
import com.runconnect.app.ui.theme.TealPrimary
import com.runconnect.app.ui.theme.TextPrimary
import com.runconnect.app.ui.theme.TextSecondary
import com.runconnect.app.utils.FormatUtils

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
                        Text(
                            FormatUtils.formatFullDate(activity.startTime) + " · " +
                                    FormatUtils.formatTime(activity.startTime),
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
                            value = FormatUtils.formatElevation(activity.elevationGainMeters),
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

        // Heart rate chart
        if (state.hrChartData.size >= 2) {
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
                    SectionHeader("Heart Rate")
                    Spacer(Modifier.height(10.dp))
                    HeartRateChart(
                        hrData = state.hrChartData,
                        avgHr = activity.averageHeartRate ?: 0,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        // Race predictions (only for runs)
        if (activity.type == ActivityType.RUNNING && state.racePredictions.isNotEmpty()) {
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
                    RacePredictionsCard(
                        predictions = state.racePredictions,
                    )
                }
            }
        }

        // Lap splits
        if (activity.laps.isNotEmpty()) {
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
                    LapSplitsCard(
                        laps = activity.laps,
                        useImperial = state.useImperial,
                    )
                }
            }
        }
    }
}
