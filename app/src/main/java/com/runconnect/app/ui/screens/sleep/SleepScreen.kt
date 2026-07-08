package com.runconnect.app.ui.screens.sleep

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.runconnect.app.domain.model.BedtimeStats
import com.runconnect.app.domain.model.MonthlySleepReport
import com.runconnect.app.domain.model.SleepAnnotation
import com.runconnect.app.domain.model.SleepCorrelation
import com.runconnect.app.domain.model.SleepDebtInfo
import com.runconnect.app.domain.model.SleepPrediction
import com.runconnect.app.domain.model.SleepRecommendation
import com.runconnect.app.domain.model.SleepSession
import com.runconnect.app.domain.model.SleepStageType
import com.runconnect.app.domain.model.WakeTimeStats
import com.runconnect.app.domain.model.WeeklySleepReport
import com.runconnect.app.ui.components.SectionHeader
import com.runconnect.app.ui.components.SmallStatItem
import com.runconnect.app.ui.theme.AmberAccent
import com.runconnect.app.ui.theme.Background
import com.runconnect.app.ui.theme.CardDark
import com.runconnect.app.ui.theme.CoralAccent
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

@Composable
fun SleepScreen(
    onSessionClick: (String) -> Unit,
    viewModel: SleepViewModel = hiltViewModel(),
) {
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
            val subtitle = when {
                state.sourceFilter == null -> "Last 90 days · All Sources"
                else -> {
                    val name = state.availableSources.find { it.first == state.sourceFilter }?.second ?: state.sourceFilter ?: ""
                    "Last 90 days · $name"
                }
            }
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }

        // Source filter chips
        if (state.availableSources.size > 1) {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = state.sourceFilter == null,
                    onClick = { viewModel.setSourceFilter(null) },
                    label = { Text("All Sources") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = TealPrimary,
                        selectedLabelColor = MaterialTheme.colorScheme.background,
                        labelColor = TextSecondary,
                    ),
                )
                state.availableSources.forEach { (pkg, name) ->
                    FilterChip(
                        selected = state.sourceFilter == pkg,
                        onClick = { viewModel.setSourceFilter(pkg) },
                        label = { Text(name) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = TealPrimary,
                            selectedLabelColor = MaterialTheme.colorScheme.background,
                            labelColor = TextSecondary,
                        ),
                    )
                }
            }
        }

        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = TealPrimary, modifier = Modifier.size(32.dp))
            }
            return
        }

        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {

            // Last Night Overview
            val lastNight = state.mainSessions.firstOrNull()
            if (lastNight != null) {
                item { LastNightOverviewCard(lastNight) }
            }

            // Debt + Prediction
            if (state.debtInfo != null || state.prediction != null) {
                item {
                    DebtPredictionCard(
                        debt = state.debtInfo,
                        prediction = state.prediction,
                        modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp).fillMaxWidth(),
                    )
                }
            }

            // Consistency
            if (state.bedtimeStats != null || state.wakeTimeStats != null) {
                item {
                    ConsistencyCard(
                        bedtime = state.bedtimeStats,
                        wake = state.wakeTimeStats,
                        modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp).fillMaxWidth(),
                    )
                }
            }

            // Naps
            if (state.napSessions.isNotEmpty()) {
                item {
                    SectionHeader(
                        "Recent Naps",
                        modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp),
                    )
                }
                items(state.napSessions.take(5), key = { "nap_${it.id}" }) { nap ->
                    NapCard(nap, modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp).fillMaxWidth())
                }
            }

            // Weekly Reports
            if (state.weeklyReports.isNotEmpty()) {
                item {
                    SectionHeader(
                        "Weekly Reports",
                        modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp),
                    )
                    WeeklyReportsRow(state.weeklyReports)
                }
            }

            // Monthly Summary
            state.monthlyReport?.let { monthly ->
                item {
                    MonthlySummaryCard(
                        monthly = monthly,
                        modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp).fillMaxWidth(),
                    )
                }
            }

            // Recommendations
            if (state.recommendations.isNotEmpty()) {
                item {
                    SectionHeader(
                        "Recommendations",
                        modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp),
                    )
                }
                items(state.recommendations, key = { "rec_${it.title}" }) { rec ->
                    RecommendationCard(rec, modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp).fillMaxWidth())
                }
            }

            // Correlation View
            if (state.correlations.isNotEmpty()) {
                item {
                    SleepCorrelationCard(
                        correlations = state.correlations,
                        modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp).fillMaxWidth(),
                    )
                }
            }

            // Recent Nights
            item {
                SectionHeader(
                    "Recent Nights",
                    modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 10.dp, top = 8.dp),
                )
            }

            if (state.mainSessions.isEmpty()) {
                item {
                    Box(
                        Modifier.fillMaxWidth().padding(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No sleep data found.\nMake sure Garmin or Withings is syncing to Health Connect.",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            items(state.mainSessions.take(30), key = { it.id }) { session ->
                SleepSessionCard(
                    session = session,
                    onClick = { onSessionClick(session.id) },
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
private fun LastNightOverviewCard(session: SleepSession, modifier: Modifier = Modifier) {
    val zoneId = session.startZoneOffset ?: ZoneId.systemDefault()
    val timeFmt = DateTimeFormatter.ofPattern("h:mm a")

    Box(
        modifier = modifier
            .padding(horizontal = 20.dp)
            .padding(bottom = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardDark)
            .padding(20.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Last Night",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = TextPrimary,
                )
                SleepScorePill(session.sleepScore)
            }
            Text(
                "${FormatUtils.formatTime(session.startTime)} – ${FormatUtils.formatTime(session.endTime)}",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
            Spacer(Modifier.height(14.dp))
            SleepStageBar(session, height = 16.dp)
            Spacer(Modifier.height(14.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SmallStatItem("Total Sleep", FormatUtils.formatSleepDuration(session.totalSleepMinutes))
                SmallStatItem("In Bed", FormatUtils.formatSleepDuration(session.totalDurationMinutes))
                SmallStatItem("Efficiency", "${session.sleepEfficiencyPercent}%", valueColor = TealPrimary)
            }
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SmallStatItem("Deep", FormatUtils.formatSleepDuration(session.deepSleepMinutes), valueColor = SleepDeep)
                SmallStatItem("REM", FormatUtils.formatSleepDuration(session.remSleepMinutes), valueColor = SleepRem)
                SmallStatItem("Awake", FormatUtils.formatSleepDuration(session.awakeMinutes), valueColor = SleepAwake)
            }
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = DividerColor, thickness = 0.5.dp)
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SmallStatItem("Latency", "${session.sleepLatencyMinutes}m")
                SmallStatItem("WASO", "${session.wasoMinutes}m")
                SmallStatItem("Awakenings", "${session.awakeningCount}")
            }
            if (session.midpointTime != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Sleep midpoint: ${timeFmt.format(session.midpointTime!!.atZone(ZoneId.systemDefault()))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun DebtPredictionCard(debt: SleepDebtInfo?, prediction: SleepPrediction?, modifier: Modifier = Modifier) {
    Box(modifier = modifier.clip(RoundedCornerShape(16.dp)).background(CardDark).padding(20.dp)) {
        Column {
            Text(
                "Sleep Debt & Tonight",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = TextPrimary,
            )
            Spacer(Modifier.height(14.dp))
            if (debt != null) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    SmallStatItem(
                        "Last Night Debt",
                        if (debt.lastNightDebt == 0L) "None" else "${debt.lastNightDebt / 60}h ${debt.lastNightDebt % 60}m",
                        valueColor = if (debt.lastNightDebt > 60) CoralAccent else TealPrimary,
                    )
                    SmallStatItem(
                        "7-Day Debt",
                        "${debt.sevenDayDebt / 60}h ${debt.sevenDayDebt % 60}m",
                        valueColor = if (debt.sevenDayDebt > 300) CoralAccent else AmberAccent,
                    )
                    SmallStatItem("Sleep Need", "${debt.sleepNeed / 60}h ${debt.sleepNeed % 60}m")
                }
            }
            if (prediction != null) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = DividerColor, thickness = 0.5.dp)
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    SmallStatItem("Predicted Tonight", FormatUtils.formatSleepDuration(prediction.predictedDurationMinutes))
                    SmallStatItem("Predicted Score", "${prediction.predictedScore}")
                    if (prediction.recommendedBedtime != null) {
                        val fmt = DateTimeFormatter.ofPattern("h:mm a")
                        SmallStatItem("Suggested Bed", fmt.format(prediction.recommendedBedtime), valueColor = PurpleAccent)
                    }
                }
            }
        }
    }
}

@Composable
private fun ConsistencyCard(bedtime: BedtimeStats?, wake: WakeTimeStats?, modifier: Modifier = Modifier) {
    Box(modifier = modifier.clip(RoundedCornerShape(16.dp)).background(CardDark).padding(20.dp)) {
        Column {
            Text(
                "Sleep Schedule Consistency",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = TextPrimary,
            )
            Spacer(Modifier.height(14.dp))

            if (bedtime != null) {
                Text("Bedtime", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                Spacer(Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    SmallStatItem("Avg", formatMinutesPastMidnight(bedtime.avgMinutes))
                    SmallStatItem("Earliest", formatMinutesPastMidnight(bedtime.earliestMinutes))
                    SmallStatItem("Latest", formatMinutesPastMidnight(bedtime.latestMinutes))
                    SmallStatItem("±Variability", "${bedtime.stdDevMinutes.toInt()}m",
                        valueColor = if (bedtime.stdDevMinutes > 60) CoralAccent else TealPrimary)
                }
                if (bedtime.weekdayAvgMinutes != bedtime.avgMinutes || bedtime.weekendAvgMinutes != bedtime.avgMinutes) {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Weekday: ${formatMinutesPastMidnight(bedtime.weekdayAvgMinutes)}",
                            style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        Text("Weekend: ${formatMinutesPastMidnight(bedtime.weekendAvgMinutes)}",
                            style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }
                }
            }

            if (wake != null) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = DividerColor, thickness = 0.5.dp)
                Spacer(Modifier.height(12.dp))
                Text("Wake Time", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                Spacer(Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    SmallStatItem("Avg", formatMinutesPastMidnight(wake.avgMinutes))
                    SmallStatItem("Earliest", formatMinutesPastMidnight(wake.earliestMinutes))
                    SmallStatItem("Latest", formatMinutesPastMidnight(wake.latestMinutes))
                    SmallStatItem("±Variability", "${wake.stdDevMinutes.toInt()}m",
                        valueColor = if (wake.stdDevMinutes > 60) CoralAccent else TealPrimary)
                }
            }
        }
    }
}

@Composable
private fun NapCard(session: SleepSession, modifier: Modifier = Modifier) {
    Box(modifier = modifier.clip(RoundedCornerShape(12.dp)).background(CardDark).padding(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(FormatUtils.formatRelativeDate(session.startTime), style = MaterialTheme.typography.labelMedium, color = TextPrimary)
                Text(
                    "${FormatUtils.formatTime(session.startTime)} – ${FormatUtils.formatTime(session.endTime)}",
                    style = MaterialTheme.typography.labelSmall, color = TextSecondary,
                )
            }
            SmallStatItem("Duration", FormatUtils.formatSleepDuration(session.totalDurationMinutes))
        }
    }
}

@Composable
private fun WeeklyReportsRow(reports: List<WeeklySleepReport>) {
    val dateFmt = DateTimeFormatter.ofPattern("MMM d")
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(bottom = 16.dp),
    ) {
        items(reports, key = { it.weekStartDate.toString() }) { report ->
            Box(
                modifier = Modifier
                    .width(140.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(CardDark)
                    .padding(14.dp)
            ) {
                Column {
                    Text(
                        "Week of ${dateFmt.format(report.weekStartDate)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        FormatUtils.formatSleepDuration(report.avgDurationMinutes),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = TextPrimary,
                    )
                    Text("avg/night", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Spacer(Modifier.height(6.dp))
                    Text("Score: ${report.avgScore}", style = MaterialTheme.typography.labelSmall, color = TealPrimary)
                    Text("Nights: ${report.nightCount}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun MonthlySummaryCard(monthly: MonthlySleepReport, modifier: Modifier = Modifier) {
    Box(modifier = modifier.clip(RoundedCornerShape(16.dp)).background(CardDark).padding(20.dp)) {
        Column {
            Text(
                "${monthly.month.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${monthly.month.year}",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = TextPrimary,
            )
            Spacer(Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SmallStatItem("Avg Sleep", FormatUtils.formatSleepDuration(monthly.avgDurationMinutes))
                SmallStatItem("Avg Score", "${monthly.avgScore}", valueColor = TealPrimary)
                SmallStatItem("Longest", FormatUtils.formatSleepDuration(monthly.longestSleepMinutes))
            }
            if (monthly.weekdayAvgMinutes > 0 || monthly.weekendAvgMinutes > 0) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Weekday avg: ${FormatUtils.formatSleepDuration(monthly.weekdayAvgMinutes)}",
                        style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Text("Weekend avg: ${FormatUtils.formatSleepDuration(monthly.weekendAvgMinutes)}",
                        style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun RecommendationCard(rec: SleepRecommendation, modifier: Modifier = Modifier) {
    val accentColor = when (rec.priority) {
        3 -> CoralAccent
        2 -> AmberAccent
        else -> TealPrimary
    }
    Box(modifier = modifier.clip(RoundedCornerShape(12.dp)).background(CardDark).padding(14.dp)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(8.dp).clip(CircleShape).background(accentColor)
                )
                Spacer(Modifier.width(8.dp))
                Text(rec.title, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = TextPrimary)
            }
            Spacer(Modifier.height(4.dp))
            Text(rec.body, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}

@Composable
private fun SleepCorrelationCard(correlations: List<SleepCorrelation>, modifier: Modifier = Modifier) {
    Box(modifier = modifier.clip(RoundedCornerShape(16.dp)).background(CardDark).padding(20.dp)) {
        Column {
            Text(
                "Sleep Factor Correlations",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = TextPrimary,
            )
            Text(
                "Based on your logged tags",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
            )
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("Factor", modifier = Modifier.weight(1.5f), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                Text("Avg Sleep", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = TextSecondary, textAlign = TextAlign.End)
                Text("vs Normal", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = TextSecondary, textAlign = TextAlign.End)
                Text("Score", modifier = Modifier.weight(0.8f), style = MaterialTheme.typography.labelSmall, color = TextSecondary, textAlign = TextAlign.End)
            }
            HorizontalDivider(color = DividerColor, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 6.dp))
            correlations.forEach { corr ->
                val delta = corr.avgDurationWith - corr.avgDurationWithout
                val deltaColor = if (delta >= 0) TealPrimary else CoralAccent
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(corr.tagLabel, modifier = Modifier.weight(1.5f), style = MaterialTheme.typography.labelSmall, color = TextPrimary)
                    Text(
                        FormatUtils.formatSleepDuration(corr.avgDurationWith),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextPrimary,
                        textAlign = TextAlign.End,
                    )
                    Text(
                        "${if (delta >= 0) "+" else ""}${delta}m",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = deltaColor,
                        textAlign = TextAlign.End,
                    )
                    Text(
                        "${corr.avgScoreWith}",
                        modifier = Modifier.weight(0.8f),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextPrimary,
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}

@Composable
internal fun SleepSessionCard(session: SleepSession, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(CardDark)
            .clickable { onClick() }
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
                SleepScorePill(session.sleepScore)
            }
            Text(
                "${FormatUtils.formatTime(session.startTime)} – ${FormatUtils.formatTime(session.endTime)}",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
            Spacer(Modifier.height(10.dp))
            SleepStageBar(session)
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                SmallStatItem("Total", FormatUtils.formatSleepDuration(session.totalSleepMinutes))
                SmallStatItem("Deep", FormatUtils.formatSleepDuration(session.deepSleepMinutes), valueColor = SleepDeep)
                SmallStatItem("REM", FormatUtils.formatSleepDuration(session.remSleepMinutes), valueColor = SleepRem)
                SmallStatItem("Awake", FormatUtils.formatSleepDuration(session.awakeMinutes), valueColor = SleepAwake)
            }
        }
    }
}

@Composable
internal fun SleepScorePill(score: Int) {
    val color = when {
        score >= 80 -> TealPrimary
        score >= 60 -> AmberAccent
        else -> CoralAccent
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.18f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text("$score", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = color)
    }
}

@Composable
internal fun SleepStageBar(session: SleepSession, height: androidx.compose.ui.unit.Dp = 12.dp) {
    val totalDuration = (session.endTime.epochSecond - session.startTime.epochSecond).toFloat()
    if (totalDuration <= 0 || session.stages.isEmpty()) return

    androidx.compose.foundation.Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
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
            drawRect(
                color = color,
                topLeft = androidx.compose.ui.geometry.Offset(start, 0f),
                size = androidx.compose.ui.geometry.Size(end - start, h),
            )
        }
    }
}

@Composable
private fun SleepStageLegend() {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SleepStageLegendDot("Deep", SleepDeep)
        SleepStageLegendDot("Light", SleepLight)
        SleepStageLegendDot("REM", SleepRem)
        SleepStageLegendDot("Awake", SleepAwake)
    }
}

@Composable
private fun SleepStageLegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    }
}

private fun formatMinutesPastMidnight(minutes: Int): String {
    val normalizedMins = minutes % 1440
    val h = normalizedMins / 60
    val m = normalizedMins % 60
    val ampm = if (h < 12) "AM" else "PM"
    val displayH = when {
        h == 0 -> 12
        h > 12 -> h - 12
        else -> h
    }
    return "${displayH}:${m.toString().padStart(2, '0')} $ampm"
}
