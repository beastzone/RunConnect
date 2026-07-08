package com.runconnect.app.ui.screens.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.runconnect.app.domain.model.HealthInsight
import com.runconnect.app.domain.model.InsightPriority
import com.runconnect.app.domain.model.InsightType
import com.runconnect.app.ui.components.ActivityCard
import com.runconnect.app.ui.components.SectionHeader
import com.runconnect.app.ui.components.StatCard
import com.runconnect.app.ui.theme.AmberAccent
import com.runconnect.app.ui.theme.Background
import com.runconnect.app.ui.theme.BlueAccent
import com.runconnect.app.ui.theme.CardDark
import com.runconnect.app.ui.theme.CoralAccent
import com.runconnect.app.ui.theme.HeartRate
import com.runconnect.app.ui.theme.PurpleAccent
import com.runconnect.app.ui.theme.TealPrimary
import com.runconnect.app.ui.theme.TextPrimary
import com.runconnect.app.ui.theme.TextSecondary
import com.runconnect.app.utils.FormatUtils

@Composable
fun DashboardScreen(
    onActivityClick: (String) -> Unit,
    onSeeAllActivities: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Background),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            Column(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Text(
                    text = "RunConnect",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = TealPrimary,
                    )
                )
                Text(
                    text = "Your performance hub",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
        }

        if (state.isLoading) {
            item {
                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = TealPrimary, modifier = Modifier.size(32.dp))
                }
            }
            return@LazyColumn
        }

        state.error?.let { error ->
            item {
                ErrorCard(error, modifier = Modifier.padding(horizontal = 20.dp))
            }
        }

        // Health Score Card
        item {
            HealthScoreCard(
                state = state,
                modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp),
            )
        }

        // Today's metrics
        item {
            Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
                SectionHeader("Today")
                Spacer(Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        DailyMetricCard(
                            label = "Steps",
                            value = if (state.todaySteps > 0) "%,d".format(state.todaySteps) else "--",
                            icon = Icons.Filled.DirectionsRun,
                            color = TealPrimary,
                        )
                    }
                    item {
                        DailyMetricCard(
                            label = "Calories",
                            value = if (state.todayActiveCalories > 0) "${state.todayActiveCalories.toInt()} kcal" else "--",
                            icon = Icons.Filled.LocalFireDepartment,
                            color = CoralAccent,
                        )
                    }
                    item {
                        DailyMetricCard(
                            label = "Last Night",
                            value = if (state.lastNightSleepMinutes > 0)
                                FormatUtils.formatSleepDuration(state.lastNightSleepMinutes) else "--",
                            icon = Icons.Filled.Schedule,
                            color = BlueAccent,
                        )
                    }
                    state.todayRestingHr?.let { rhr ->
                        item {
                            DailyMetricCard(
                                label = "Resting HR",
                                value = "$rhr bpm",
                                icon = Icons.Filled.Favorite,
                                color = HeartRate,
                            )
                        }
                    }
                    state.todayHrv?.let { hrv ->
                        item {
                            DailyMetricCard(
                                label = "HRV",
                                value = "${hrv.toInt()} ms",
                                icon = Icons.Filled.Favorite,
                                color = PurpleAccent,
                            )
                        }
                    }
                }
            }
        }

        // Insights
        if (state.insights.isNotEmpty()) {
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
                    SectionHeader("Insights")
                    Spacer(Modifier.height(12.dp))
                    state.insights.forEach { insight ->
                        InsightCard(insight)
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }

        // Weekly summary cards
        item {
            Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 4.dp)) {
                SectionHeader("This Week")
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard(
                        label = "Distance",
                        value = if (state.useImperial) "%.1f".format(state.weeklyDistanceKm * 0.621371)
                        else "%.1f".format(state.weeklyDistanceKm),
                        unit = if (state.useImperial) "mi" else "km",
                        icon = Icons.Filled.Straighten,
                        accentColor = TealPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    StatCard(
                        label = "Activities",
                        value = "${state.weeklyActivityCount}",
                        icon = Icons.Filled.DirectionsRun,
                        accentColor = CoralAccent,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard(
                        label = "Time",
                        value = FormatUtils.formatDuration(state.weeklyTimeSeconds),
                        icon = Icons.Filled.Schedule,
                        accentColor = BlueAccent,
                        modifier = Modifier.weight(1f),
                    )
                    StatCard(
                        label = "Day Streak",
                        value = "${state.streakDays}",
                        unit = if (state.streakDays == 1) "day" else "days",
                        icon = Icons.Filled.LocalFireDepartment,
                        accentColor = AmberAccent,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // Recent activities
        item {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {
                SectionHeader(
                    title = "Recent Activities",
                    trailing = {
                        TextButton(onClick = onSeeAllActivities) {
                            Text("See All", color = TealPrimary, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                )
            }
        }

        if (state.recentActivities.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(CardDark)
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No activities found.\nMake sure Garmin is syncing to Health Connect.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }

        items(state.recentActivities, key = { it.id }) { activity ->
            ActivityCard(
                activity = activity,
                onClick = { onActivityClick(activity.id) },
                useImperial = state.useImperial,
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 10.dp)
                    .fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun HealthScoreCard(state: DashboardUiState, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CardDark)
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    "Health Score",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = TextSecondary,
                )
                Text(
                    "${state.overallScore}",
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 52.sp,
                    ),
                    color = scoreColor(state.overallScore),
                )
                Text(
                    scoreLabel(state.overallScore),
                    style = MaterialTheme.typography.bodySmall,
                    color = scoreColor(state.overallScore),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ScoreRing(score = state.sleepScore, label = "Sleep", color = BlueAccent)
                ScoreRing(score = state.activityScore, label = "Activity", color = TealPrimary)
                ScoreRing(score = state.recoveryScore, label = "Recovery", color = PurpleAccent)
            }
        }
    }
}

@Composable
private fun ScoreRing(score: Int, label: String, color: Color) {
    val progress by animateFloatAsState(
        targetValue = score / 100f,
        animationSpec = tween(durationMillis = 800),
        label = "ring_$label",
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(52.dp), contentAlignment = Alignment.Center) {
            androidx.compose.foundation.Canvas(modifier = Modifier.size(52.dp)) {
                val stroke = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
                drawArc(color = color.copy(alpha = 0.15f), startAngle = -90f, sweepAngle = 360f, useCenter = false, style = stroke)
                drawArc(color = color, startAngle = -90f, sweepAngle = progress * 360f, useCenter = false, style = stroke)
            }
            Text(
                "$score",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = color,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    }
}

@Composable
private fun DailyMetricCard(label: String, value: String, icon: ImageVector, color: Color) {
    Box(
        modifier = Modifier
            .width(100.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(CardDark)
            .padding(14.dp)
    ) {
        Column {
            Box(
                modifier = Modifier.size(28.dp).clip(CircleShape).background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary,
                maxLines = 1,
            )
            Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        }
    }
}

@Composable
private fun InsightCard(insight: HealthInsight) {
    val priorityColor = when (insight.priority) {
        InsightPriority.HIGH -> CoralAccent
        InsightPriority.MEDIUM -> AmberAccent
        InsightPriority.LOW -> TealPrimary
    }
    val typeColor = when (insight.type) {
        InsightType.SLEEP -> BlueAccent
        InsightType.RECOVERY -> PurpleAccent
        InsightType.TRAINING -> TealPrimary
        InsightType.CONSISTENCY -> AmberAccent
    }
    val typeLabel = insight.type.name.lowercase().replaceFirstChar { it.uppercase() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardDark)
            .padding(14.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(4.dp))
                        .background(typeColor.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(typeLabel, style = MaterialTheme.typography.labelSmall, color = typeColor)
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(4.dp))
                        .background(priorityColor.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        insight.priority.name.lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall,
                        color = priorityColor,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                insight.title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = TextPrimary,
            )
            Spacer(Modifier.height(4.dp))
            Text(insight.body, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            insight.actionHint?.let { hint ->
                Spacer(Modifier.height(4.dp))
                Text("→ $hint", style = MaterialTheme.typography.labelSmall, color = typeColor)
            }
        }
    }
}

private fun scoreColor(score: Int): Color = when {
    score >= 80 -> TealPrimary
    score >= 60 -> AmberAccent
    else -> CoralAccent
}

private fun scoreLabel(score: Int): String = when {
    score >= 90 -> "Peak"
    score >= 80 -> "Great"
    score >= 70 -> "Good"
    score >= 60 -> "Fair"
    score >= 50 -> "Low"
    else -> "Rest needed"
}

@Composable
private fun ErrorCard(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(com.runconnect.app.ui.theme.ErrorContainer)
            .padding(16.dp)
    ) {
        Text(message, color = com.runconnect.app.ui.theme.ErrorColor, style = MaterialTheme.typography.bodyMedium)
    }
}
