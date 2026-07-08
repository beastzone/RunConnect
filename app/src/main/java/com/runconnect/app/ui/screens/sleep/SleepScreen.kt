package com.runconnect.app.ui.screens.sleep

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.runconnect.app.domain.model.SleepSession
import com.runconnect.app.domain.model.SleepStageType
import com.runconnect.app.ui.components.SectionHeader
import com.runconnect.app.ui.components.SmallStatItem
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

@Composable
fun SleepScreen(viewModel: SleepViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background),
    ) {
        Column(
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Text(
                "Sleep",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary,
            )
            Text("Last 30 days", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }

        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = TealPrimary, modifier = Modifier.size(32.dp))
            }
            return
        }

        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
            // Averages card
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
                    Column {
                        Text(
                            "30-Day Averages",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = TextPrimary,
                        )
                        Spacer(Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            SmallStatItem(
                                label = "Total Sleep",
                                value = FormatUtils.formatSleepDuration(state.avgDurationMinutes),
                            )
                            SmallStatItem(
                                label = "Deep Sleep",
                                value = FormatUtils.formatSleepDuration(state.avgDeepMinutes),
                                valueColor = SleepDeep,
                            )
                            SmallStatItem(
                                label = "REM Sleep",
                                value = FormatUtils.formatSleepDuration(state.avgRemMinutes),
                                valueColor = SleepRem,
                            )
                            SmallStatItem(
                                label = "Efficiency",
                                value = "${state.avgEfficiency}%",
                                valueColor = TealPrimary,
                            )
                        }
                    }
                }
            }

            // Sleep stage legend
            item {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SleepStageLegendItem("Deep", SleepDeep)
                    SleepStageLegendItem("Light", SleepLight)
                    SleepStageLegendItem("REM", SleepRem)
                    SleepStageLegendItem("Awake", SleepAwake)
                }
            }

            // Recent sessions
            item {
                SectionHeader(
                    "Recent Nights",
                    modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 10.dp)
                )
            }

            if (state.sessions.isEmpty()) {
                item {
                    Box(
                        Modifier.fillMaxWidth().padding(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No sleep data found.\nMake sure Garmin or Withings is syncing to Health Connect.",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            }

            items(state.sessions.take(14), key = { it.id }) { session ->
                SleepSessionCard(
                    session = session,
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 10.dp)
                        .fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun SleepSessionCard(session: SleepSession, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(CardDark)
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    FormatUtils.formatRelativeDate(session.startTime),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = TextPrimary,
                )
                Text(
                    "${session.sleepEfficiencyPercent}% efficient",
                    style = MaterialTheme.typography.labelSmall,
                    color = TealPrimary,
                )
            }
            Text(
                "${FormatUtils.formatTime(session.startTime)} – ${FormatUtils.formatTime(session.endTime)}",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
            Spacer(Modifier.height(10.dp))

            // Sleep stage timeline bar
            SleepStageBar(session)

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                SmallStatItem(
                    label = "Total",
                    value = FormatUtils.formatSleepDuration(session.totalDurationMinutes),
                )
                SmallStatItem(
                    label = "Deep",
                    value = FormatUtils.formatSleepDuration(session.deepSleepMinutes),
                    valueColor = SleepDeep,
                )
                SmallStatItem(
                    label = "REM",
                    value = FormatUtils.formatSleepDuration(session.remSleepMinutes),
                    valueColor = SleepRem,
                )
                SmallStatItem(
                    label = "Awake",
                    value = FormatUtils.formatSleepDuration(session.awakeMinutes),
                    valueColor = SleepAwake,
                )
            }
        }
    }
}

@Composable
private fun SleepStageBar(session: SleepSession) {
    val totalDuration = (session.endTime.epochSecond - session.startTime.epochSecond).toFloat()
    if (totalDuration <= 0 || session.stages.isEmpty()) return

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(RoundedCornerShape(6.dp))
    ) {
        val w = size.width
        val h = size.height
        session.stages.forEach { stage ->
            val start = ((stage.startTime.epochSecond - session.startTime.epochSecond) / totalDuration) * w
            val end = ((stage.endTime.epochSecond - session.startTime.epochSecond) / totalDuration) * w
            val color = when (stage.type) {
                SleepStageType.DEEP -> SleepDeep
                SleepStageType.LIGHT -> SleepLight
                SleepStageType.REM -> SleepRem
                SleepStageType.AWAKE -> SleepAwake
                SleepStageType.UNKNOWN -> Color(0xFF2A3242)
            }
            drawRect(color = color, topLeft = Offset(start, 0f), size = Size(end - start, h))
        }
    }
}

@Composable
private fun SleepStageLegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    }
}
