package com.runconnect.app.ui.screens.sleep

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.runconnect.app.domain.model.SleepAnnotation
import com.runconnect.app.ui.components.SmallStatItem
import com.runconnect.app.ui.theme.AmberAccent
import com.runconnect.app.ui.theme.Background
import com.runconnect.app.ui.theme.BlueAccent
import com.runconnect.app.ui.theme.CardDark
import com.runconnect.app.ui.theme.DividerColor
import com.runconnect.app.ui.theme.PurpleAccent
import com.runconnect.app.ui.theme.SleepAwake
import com.runconnect.app.ui.theme.SleepDeep
import com.runconnect.app.ui.theme.SleepLight
import com.runconnect.app.ui.theme.SleepRem
import com.runconnect.app.ui.theme.TealPrimary
import com.runconnect.app.ui.theme.TextPrimary
import com.runconnect.app.ui.theme.TextSecondary
import com.runconnect.app.utils.FormatUtils
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

@Composable
fun SleepDetailScreen(
    sessionId: String,
    onBack: () -> Unit,
    viewModel: SleepDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var localAnnotation by remember { mutableStateOf(SleepAnnotation()) }

    // Sync local annotation from state whenever it loads/changes externally
    LaunchedEffect(state.annotation) {
        localAnnotation = state.annotation
    }

    // Debounced auto-save on annotation change
    LaunchedEffect(localAnnotation) {
        if (!state.isLoading) {
            delay(300)
            viewModel.saveAnnotation(localAnnotation)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background),
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }
            val session = state.session
            if (session != null) {
                Column {
                    Text(
                        FormatUtils.formatFullDate(session.startTime),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = TextPrimary,
                    )
                    Text(
                        "${FormatUtils.formatTime(session.startTime)} – ${FormatUtils.formatTime(session.endTime)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
            } else {
                Text("Sleep Detail", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            }
        }

        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = TealPrimary, modifier = Modifier.size(32.dp))
            }
            return
        }

        val session = state.session
        if (session == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Session not found", color = TextSecondary)
            }
            return
        }

        LazyColumn(contentPadding = PaddingValues(bottom = 40.dp)) {

            // 11.1 Overview metrics
            item {
                DetailCard(title = "Overview") {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        SmallStatItem("Sleep Score", "${session.sleepScore}",
                            valueColor = when {
                                session.sleepScore >= 80 -> TealPrimary
                                session.sleepScore >= 60 -> AmberAccent
                                else -> com.runconnect.app.ui.theme.CoralAccent
                            })
                        SmallStatItem("Efficiency", "${session.sleepEfficiencyPercent}%", valueColor = TealPrimary)
                        SmallStatItem("Total Sleep", FormatUtils.formatSleepDuration(session.totalSleepMinutes))
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        val latencyLabel = if (state.annotation.latencyCorrectionMinutes != 0) {
                            val corrected = session.sleepLatencyMinutes + state.annotation.latencyCorrectionMinutes
                            "${corrected}m (adj)"
                        } else {
                            "${session.sleepLatencyMinutes}m"
                        }
                        SmallStatItem("Latency", latencyLabel)
                        SmallStatItem("WASO", "${session.wasoMinutes}m")
                        SmallStatItem("Awakenings", "${session.awakeningCount}")
                    }
                    if (session.midpointTime != null) {
                        Spacer(Modifier.height(8.dp))
                        val timeFmt = DateTimeFormatter.ofPattern("h:mm a")
                        Text(
                            "Sleep midpoint: ${timeFmt.format(session.midpointTime!!.atZone(ZoneId.systemDefault()))}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                        )
                    }
                }
            }

            // 11.2 Interactive Stage Timeline
            item {
                DetailCard(title = "Sleep Stages") {
                    SleepStageTimeline(session = session, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                    SleepStageLegendRow()
                }
            }

            // 11.3 Stage Detail (11.4 efficiency is in overview above)
            item {
                DetailCard(title = "Stage Detail") {
                    StageDetailSection(
                        session = session,
                        historicalAvgDeep = state.historicalAvgDeep,
                        historicalAvgLight = state.historicalAvgLight,
                        historicalAvgRem = state.historicalAvgRem,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // 11.13 Overnight HR
            item {
                val hrSamples = session.heartRateSamples.map { it.first to it.second.toDouble() }
                DetailCardRaw {
                    Column(modifier = Modifier.padding(16.dp)) {
                        OvernightLineChart(
                            samples = hrSamples,
                            title = "Overnight Heart Rate",
                            unit = "bpm",
                            lineColor = com.runconnect.app.ui.theme.CoralAccent,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (hrSamples.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            HorizontalDivider(color = DividerColor, thickness = 0.5.dp)
                            Spacer(Modifier.height(10.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                SmallStatItem("Avg HR", "${session.avgHeartRate ?: "–"}bpm")
                                SmallStatItem("Lowest HR", "${session.lowestHeartRate ?: "–"}bpm")
                                if (session.timeOfLowestHr != null) {
                                    val timeFmt = DateTimeFormatter.ofPattern("h:mm a")
                                    SmallStatItem("Time of Low", timeFmt.format(session.timeOfLowestHr!!.atZone(ZoneId.systemDefault())))
                                }
                            }
                        }
                    }
                }
            }

            // 11.14 Overnight HRV
            item {
                DetailCardRaw {
                    Column(modifier = Modifier.padding(16.dp)) {
                        OvernightLineChart(
                            samples = session.hrvSamples,
                            title = "Overnight HRV",
                            unit = "ms",
                            lineColor = PurpleAccent,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (session.hrvSamples.isNotEmpty() && session.avgHrv != null) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Avg: ${"%.1f".format(session.avgHrv)} ms · Range: ${session.hrvSamples.minOf { it.second }.toInt()}–${session.hrvSamples.maxOf { it.second }.toInt()} ms",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                            )
                        }
                    }
                }
            }

            // 11.15 Overnight Respiration
            item {
                DetailCardRaw {
                    Column(modifier = Modifier.padding(16.dp)) {
                        OvernightLineChart(
                            samples = session.respirationSamples,
                            title = "Overnight Respiration",
                            unit = "br/min",
                            lineColor = BlueAccent,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (session.avgRespiration != null) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Avg: ${"%.1f".format(session.avgRespiration)} breaths/min",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                            )
                        }
                    }
                }
            }

            // 11.16 Overnight SpO2
            item {
                DetailCardRaw {
                    Column(modifier = Modifier.padding(16.dp)) {
                        OvernightLineChart(
                            samples = session.spo2Samples,
                            title = "Overnight SpO2",
                            unit = "%",
                            lineColor = BlueAccent,
                            thresholdValue = 95.0,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (session.spo2Samples.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                SmallStatItem("Avg SpO2", "${"%.1f".format(session.avgSpo2)}%", valueColor = BlueAccent)
                                SmallStatItem("Min SpO2", "${"%.1f".format(session.minSpo2)}%",
                                    valueColor = if ((session.minSpo2 ?: 100.0) < 95.0) com.runconnect.app.ui.theme.CoralAccent else TealPrimary)
                                if (session.timeBelowSpo2Threshold > 0) {
                                    SmallStatItem("Below 95%", "${session.timeBelowSpo2Threshold}m",
                                        valueColor = AmberAccent)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "SpO2 readings are for wellness awareness only and are not intended for medical diagnosis.",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                            )
                        }
                    }
                }
            }

            // 11.17 Movement (static note)
            item {
                DetailCard(title = "Sleep Movement") {
                    Text(
                        "Movement data is not available from Health Connect. Your wearable may track this natively in its companion app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
            }

            // 11.19 Factor Tags
            item {
                DetailCard(title = "Sleep Factor Tags") {
                    SleepFactorTagGrid(
                        selectedTags = localAnnotation.tags,
                        onToggle = { key ->
                            val tags = localAnnotation.tags.toMutableList()
                            if (tags.contains(key)) tags.remove(key) else tags.add(key)
                            localAnnotation = localAnnotation.copy(tags = tags)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // 11.18 Environment Notes
            item {
                DetailCard(title = "Sleep Environment") {
                    SleepEnvironmentNotesForm(
                        annotation = localAnnotation,
                        onSave = { localAnnotation = it },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // 11.23 Smart Wake (static note)
            item {
                DetailCard(title = "Smart Wake") {
                    Text(
                        "Smart wake requires wearable alarm integration and is not currently supported. Check your Garmin Connect or Withings app for smart alarm features.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .padding(horizontal = 20.dp)
            .padding(bottom = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardDark)
            .padding(16.dp)
    ) {
        Column {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = TextPrimary,
            )
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun DetailCardRaw(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .padding(horizontal = 20.dp)
            .padding(bottom = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardDark)
    ) {
        content()
    }
}

@Composable
private fun SleepStageLegendRow() {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf("Deep" to SleepDeep, "Light" to SleepLight, "REM" to SleepRem, "Awake" to SleepAwake).forEach { (label, color) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(color))
                Spacer(Modifier.size(4.dp))
                Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            }
        }
    }
}
