package com.runconnect.app.ui.screens.heartrate

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.runconnect.app.domain.model.ActivityType
import com.runconnect.app.domain.model.ElevatedRhrAlert
import com.runconnect.app.domain.model.HrByTypeStats
import com.runconnect.app.domain.model.HrZone
import com.runconnect.app.domain.model.LowRhrAlert
import com.runconnect.app.domain.model.RhrRollingAvgs
import com.runconnect.app.domain.model.WorkoutRecoveryPoint
import com.runconnect.app.ui.components.HrZoneBar
import com.runconnect.app.ui.components.SectionHeader
import com.runconnect.app.ui.components.SmallStatItem
import com.runconnect.app.ui.components.axisLabelStyle
import com.runconnect.app.ui.components.chartScrubber
import com.runconnect.app.ui.components.drawScrubberTooltip
import com.runconnect.app.ui.components.epochSecondsToMonthDay
import com.runconnect.app.ui.components.uiColor
import com.runconnect.app.ui.theme.AmberAccent
import com.runconnect.app.ui.theme.Background
import com.runconnect.app.ui.theme.BlueAccent
import com.runconnect.app.ui.theme.CardDark
import com.runconnect.app.ui.theme.CoralAccent
import com.runconnect.app.ui.theme.HeartRate
import com.runconnect.app.ui.theme.TealPrimary
import com.runconnect.app.ui.theme.TextPrimary
import com.runconnect.app.ui.theme.TextSecondary
import com.runconnect.app.ui.theme.PurpleAccent
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun HeartRateScreen(viewModel: HeartRateViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize().background(Background)) {
        Column(
            modifier = Modifier.statusBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Text(
                "Heart Rate",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary,
            )
            Text("Last 90 days", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }

        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = TealPrimary, modifier = Modifier.size(32.dp))
            }
            return
        }

        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {

            // Current HR (12.2)
            if (state.currentHrBpm != null) {
                item {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 20.dp)
                            .padding(bottom = 16.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(CardDark)
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(
                                "Current Heart Rate",
                                style = MaterialTheme.typography.titleSmall,
                                color = TextSecondary,
                            )
                            Text(
                                "${state.currentHrBpm} bpm",
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                color = HeartRate,
                            )
                        }
                        Text(
                            "From today's data",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                    }
                }
            }

            // Summary stats
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
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        SmallStatItem(
                            label = "Avg Resting HR",
                            value = if (state.avgRestingHr > 0) "${state.avgRestingHr} bpm" else "--",
                            valueColor = TealPrimary,
                        )
                        SmallStatItem(
                            label = "Max Recorded",
                            value = if (state.maxRecordedHr > 0) "${state.maxRecordedHr} bpm" else "--",
                            valueColor = HeartRate,
                        )
                        SmallStatItem(
                            label = "HR Max Setting",
                            value = "${state.maxHrSetting} bpm",
                            valueColor = TextSecondary,
                        )
                    }
                }
            }

            // Enhanced RHR stats (12.3)
            if (state.rhrSevenDayAvg != null || state.rhrThirtyDayBaseline != null) {
                item {
                    EnhancedRhrStatsCard(
                        sevenDayAvg = state.rhrSevenDayAvg,
                        thirtyDayBaseline = state.rhrThirtyDayBaseline,
                        delta = state.rhrDeltaFromBaseline,
                        modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp),
                    )
                }
            }

            // Elevated RHR alert (12.12)
            if (state.elevatedRhrAlert != null) {
                item {
                    ElevatedRhrAlertBanner(
                        alert = state.elevatedRhrAlert,
                        modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp),
                    )
                }
            }

            // Low RHR alert (12.13)
            if (state.lowRhrAlert != null) {
                item {
                    LowRhrAlertBanner(
                        alert = state.lowRhrAlert,
                        modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp),
                    )
                }
            }

            // Resting HR trend chart (12.3)
            if (state.restingHrHistory.size >= 3) {
                item {
                    Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
                        SectionHeader("Resting HR Trend")
                        Spacer(Modifier.height(10.dp))
                        RestingHrChart(
                            data = state.restingHrHistory,
                            modifier = Modifier.fillMaxWidth().height(130.dp),
                        )
                    }
                }
            }

            // Rolling averages (12.10)
            if (state.rhrRollingAvgs != null) {
                item {
                    RollingAveragesCard(
                        avgs = state.rhrRollingAvgs,
                        modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp),
                    )
                }
            }

            // Baseline deviation (12.11)
            if (state.rhrBaselineDeviation != null) {
                item {
                    BaselineDeviationCard(
                        deviation = state.rhrBaselineDeviation,
                        modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp),
                    )
                }
            }

            // HRV trend
            if (state.hrvHistory.size >= 3) {
                item {
                    Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
                        SectionHeader("HRV Trend (30 days)")
                        Spacer(Modifier.height(10.dp))
                        HrvChart(
                            data = state.hrvHistory,
                            modifier = Modifier.fillMaxWidth().height(130.dp),
                        )
                    }
                }
            }

            // HR zones (existing)
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
                    SectionHeader("Training Zones")
                    Spacer(Modifier.height(12.dp))
                    if (state.zoneSummaries.isEmpty()) {
                        Text(
                            "No heart rate data available",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                        )
                    } else {
                        state.zoneSummaries.forEach { summary ->
                            HrZoneBar(summary)
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }

            // Recovery trends (12.14)
            if (state.recoveryTrends.size >= 3) {
                item {
                    RecoveryTrendsCard(
                        trends = state.recoveryTrends,
                        modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp),
                    )
                }
            }

            // HR by activity type (12.16)
            if (state.hrByActivityType.isNotEmpty()) {
                item {
                    HrByActivityTypeCard(
                        hrByType = state.hrByActivityType,
                        modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp),
                    )
                }
            }

            // About HR Zones (existing)
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    SectionHeader("About HR Zones")
                    Spacer(Modifier.height(8.dp))
                    listOf(
                        Triple(HrZone.ZONE_1, "< 60% max HR", "Easy / Recovery"),
                        Triple(HrZone.ZONE_2, "60–70% max HR", "Aerobic base building"),
                        Triple(HrZone.ZONE_3, "70–80% max HR", "Aerobic endurance"),
                        Triple(HrZone.ZONE_4, "80–90% max HR", "Lactate threshold"),
                        Triple(HrZone.ZONE_5, "> 90% max HR", "VO2 max / sprint"),
                    ).forEach { (zone, range, desc) ->
                        ZoneInfoRow(zone = zone, range = range, description = desc)
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun EnhancedRhrStatsCard(
    sevenDayAvg: Int?,
    thirtyDayBaseline: Int?,
    delta: Int?,
    modifier: Modifier = Modifier,
) {
    val deltaColor = when {
        delta == null -> TextSecondary
        delta > 5 -> CoralAccent
        delta > 0 -> AmberAccent
        delta < -5 -> BlueAccent
        else -> TealPrimary
    }
    val deltaSign = if ((delta ?: 0) > 0) "+" else ""

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardDark)
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            SmallStatItem(
                label = "7-day avg",
                value = if (sevenDayAvg != null) "$sevenDayAvg bpm" else "--",
                valueColor = TextPrimary,
            )
            SmallStatItem(
                label = "30-day baseline",
                value = if (thirtyDayBaseline != null) "$thirtyDayBaseline bpm" else "--",
                valueColor = TextSecondary,
            )
            if (delta != null) {
                SmallStatItem(
                    label = "vs baseline",
                    value = "$deltaSign$delta bpm",
                    valueColor = deltaColor,
                )
            }
        }
    }
}

@Composable
private fun ElevatedRhrAlertBanner(
    alert: ElevatedRhrAlert,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CoralAccent.copy(alpha = 0.10f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = CoralAccent, modifier = Modifier.size(18.dp))
            Text(
                "Resting HR elevated: ${alert.currentAvg} bpm (baseline ${alert.baseline} bpm)",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = CoralAccent,
            )
        }
        Text(
            "Possible contributing factors — not a diagnosis:",
            style = MaterialTheme.typography.labelSmall,
            color = CoralAccent.copy(alpha = 0.8f),
        )
        alert.possibleCauses.forEach { cause ->
            Text("• $cause", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}

@Composable
private fun LowRhrAlertBanner(
    alert: LowRhrAlert,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BlueAccent.copy(alpha = 0.10f))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = BlueAccent, modifier = Modifier.size(18.dp))
        Column {
            Text(
                "Unusually low resting HR: ${alert.currentAvg} bpm (baseline ${alert.baseline} bpm)",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = BlueAccent,
            )
            Text(
                "Common in well-trained athletes. May also result from sensor error.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }
    }
}

@Composable
private fun RollingAveragesCard(
    avgs: RhrRollingAvgs,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardDark)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Resting HR Rolling Averages",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = TextPrimary,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            listOf("7D" to avgs.d7, "14D" to avgs.d14, "30D" to avgs.d30, "90D" to avgs.d90)
                .forEach { (label, avg) ->
                    SmallStatItem(label = label, value = if (avg != null) "$avg bpm" else "--")
                }
        }
    }
}

@Composable
private fun BaselineDeviationCard(
    deviation: Float,
    modifier: Modifier = Modifier,
) {
    val deviationColor = when {
        abs(deviation) <= 1f -> TealPrimary
        abs(deviation) <= 2f -> AmberAccent
        else -> CoralAccent
    }
    val sign = if (deviation > 0) "+" else ""
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardDark)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                "Baseline Deviation",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = TextPrimary,
            )
            Text(
                "SD from personal mean",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }
        Text(
            "$sign${"%.1f".format(deviation)} SD",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = deviationColor,
        )
    }
}

@Composable
private fun RecoveryTrendsCard(
    trends: List<WorkoutRecoveryPoint>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardDark)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "HR Recovery Trend",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = TextPrimary,
        )
        Text(
            "1-min drop after workouts",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )
        Spacer(Modifier.height(4.dp))
        trends.takeLast(10).forEach { point ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${point.date.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)} ${point.date.dayOfMonth}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (point.drop1Min != null) {
                        Text(
                            "1m: −${point.drop1Min}",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = if (point.drop1Min >= 20) TealPrimary
                            else if (point.drop1Min >= 12) AmberAccent
                            else TextSecondary,
                        )
                    }
                    if (point.drop5Min != null) {
                        Text(
                            "5m: −${point.drop5Min}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (point.drop5Min >= 50) TealPrimary
                            else if (point.drop5Min >= 30) AmberAccent
                            else TextSecondary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HrByActivityTypeCard(
    hrByType: Map<ActivityType, HrByTypeStats>,
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
            "HR by Activity Type",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = TextPrimary,
        )
        hrByType.entries.sortedByDescending { it.value.activityCount }.forEach { (type, stats) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    type.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Avg", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        Text("${stats.avgHr}", style = MaterialTheme.typography.bodySmall, color = HeartRate)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Max", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        Text("${stats.maxHr}", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Count", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        Text("${stats.activityCount}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun RestingHrChart(data: List<Pair<Long, Int>>, modifier: Modifier = Modifier) {
    val bpms = data.map { it.second }
    val minBpm = (bpms.minOrNull() ?: 40) - 5
    val maxBpm = (bpms.maxOrNull() ?: 80) + 5
    val range = (maxBpm - minBpm).coerceAtLeast(1).toFloat()

    val textMeasurer = rememberTextMeasurer()
    var scrubberX by remember { mutableStateOf<Float?>(null) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(CardDark)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .chartScrubber { scrubberX = it }
        ) {
            val axisBottomPad = 18.dp.toPx()
            val axisRightPad = 34.dp.toPx()
            val chartW = size.width - axisRightPad
            val chartH = size.height - axisBottomPad

            fun xFor(i: Int) = (i.toFloat() / (data.size - 1)) * chartW
            fun yFor(bpm: Int) = chartH - ((bpm - minBpm).toFloat() / range * chartH * 0.8f + chartH * 0.1f)

            val pts = data.mapIndexed { i, (_, bpm) -> Offset(xFor(i), yFor(bpm)) }

            val fillPath = Path().apply {
                moveTo(pts.first().x, chartH)
                pts.forEach { lineTo(it.x, it.y) }
                lineTo(pts.last().x, chartH)
                close()
            }
            drawPath(fillPath, color = BlueAccent.copy(alpha = 0.08f))

            val linePath = Path().apply {
                pts.forEachIndexed { i, pt -> if (i == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y) }
            }
            drawPath(linePath, color = BlueAccent, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))

            pts.forEach { pt ->
                drawCircle(color = BlueAccent.copy(alpha = 0.4f), radius = 2.5.dp.toPx(), center = pt)
            }

            val axisStyle = axisLabelStyle
            val midBpm = (minBpm + maxBpm) / 2
            listOf(
                0.1f * chartH to maxBpm,
                0.5f * chartH to midBpm,
                0.9f * chartH to minBpm,
            ).forEach { (y, bpm) ->
                val label = "$bpm"
                val m = textMeasurer.measure(label, axisStyle)
                drawText(
                    textMeasurer, label,
                    topLeft = Offset(chartW + 3.dp.toPx(), (y - m.size.height.toFloat() / 2f).coerceAtLeast(0f)),
                    style = axisStyle,
                )
            }

            val midIdx = data.size / 2
            listOf(0f to data.first().first, 0.5f to data[midIdx].first, 1f to data.last().first)
                .forEach { (frac, epoch) ->
                    val label = epochSecondsToMonthDay(epoch)
                    val m = textMeasurer.measure(label, axisStyle)
                    val cx = frac * chartW
                    val lx = when {
                        frac == 0f -> cx
                        frac == 1f -> (cx - m.size.width.toFloat()).coerceAtMost(chartW - m.size.width.toFloat())
                        else -> cx - m.size.width.toFloat() / 2f
                    }
                    drawText(textMeasurer, label, topLeft = Offset(lx, chartH + 3.dp.toPx()), style = axisStyle)
                }

            scrubberX?.let { sx ->
                val idx = (sx / chartW * (data.size - 1)).roundToInt().coerceIn(0, data.size - 1)
                val pt = pts[idx]
                val (epoch, bpm) = data[idx]
                drawLine(Color.White.copy(alpha = 0.25f), Offset(pt.x, 0f), Offset(pt.x, chartH), 1.5f)
                drawCircle(BlueAccent, 5.dp.toPx(), pt)
                drawCircle(Color.White, 2.5.dp.toPx(), pt)
                drawScrubberTooltip(textMeasurer, "$bpm bpm · ${epochSecondsToMonthDay(epoch)}", pt.x, pt.y, chartW)
            }
        }
    }
}

@Composable
private fun ZoneInfoRow(zone: HrZone, range: String, description: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(CardDark)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(zone.uiColor))
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(zone.label, style = MaterialTheme.typography.labelMedium.copy(color = TextPrimary))
            Text(description, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
        Text(range, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    }
}

@Composable
private fun HrvChart(data: List<Pair<Long, Double>>, modifier: Modifier = Modifier) {
    val values = data.map { it.second }
    val minVal = ((values.minOrNull() ?: 20.0) - 5.0).coerceAtLeast(0.0)
    val maxVal = (values.maxOrNull() ?: 80.0) + 5.0
    val range = (maxVal - minVal).coerceAtLeast(1.0).toFloat()

    val textMeasurer = rememberTextMeasurer()
    var scrubberX by remember { mutableStateOf<Float?>(null) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(CardDark)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .chartScrubber { scrubberX = it }
        ) {
            val axisBottomPad = 18.dp.toPx()
            val axisRightPad = 38.dp.toPx()
            val chartW = size.width - axisRightPad
            val chartH = size.height - axisBottomPad

            fun xFor(i: Int) = (i.toFloat() / (values.size - 1)) * chartW
            fun yFor(v: Double) = chartH - (((v - minVal) / range * chartH * 0.8f + chartH * 0.1f).toFloat())

            val pts = values.mapIndexed { i, v -> Offset(xFor(i), yFor(v)) }

            val fillPath = Path().apply {
                moveTo(pts.first().x, chartH)
                pts.forEach { lineTo(it.x, it.y) }
                lineTo(pts.last().x, chartH)
                close()
            }
            drawPath(fillPath, color = PurpleAccent.copy(alpha = 0.08f))

            val linePath = Path().apply {
                pts.forEachIndexed { i, pt -> if (i == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y) }
            }
            drawPath(linePath, color = PurpleAccent, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))

            pts.forEach { pt ->
                drawCircle(color = PurpleAccent.copy(alpha = 0.4f), radius = 2.5.dp.toPx(), center = pt)
            }

            val axisStyle = axisLabelStyle
            val midVal = (minVal + maxVal) / 2
            listOf(
                0.1f * chartH to maxVal,
                0.5f * chartH to midVal,
                0.9f * chartH to minVal,
            ).forEach { (y, v) ->
                val label = "%.0f".format(v)
                val m = textMeasurer.measure(label, axisStyle)
                drawText(
                    textMeasurer, label,
                    topLeft = Offset(chartW + 3.dp.toPx(), (y - m.size.height.toFloat() / 2f).coerceAtLeast(0f)),
                    style = axisStyle,
                )
            }

            val midIdx = data.size / 2
            listOf(0f to data.first().first, 0.5f to data[midIdx].first, 1f to data.last().first)
                .forEach { (frac, epoch) ->
                    val label = epochSecondsToMonthDay(epoch)
                    val m = textMeasurer.measure(label, axisStyle)
                    val cx = frac * chartW
                    val lx = when {
                        frac == 0f -> cx
                        frac == 1f -> (cx - m.size.width.toFloat()).coerceAtMost(chartW - m.size.width.toFloat())
                        else -> cx - m.size.width.toFloat() / 2f
                    }
                    drawText(textMeasurer, label, topLeft = Offset(lx, chartH + 3.dp.toPx()), style = axisStyle)
                }

            scrubberX?.let { sx ->
                val idx = (sx / chartW * (values.size - 1)).roundToInt().coerceIn(0, values.size - 1)
                val pt = pts[idx]
                val (epoch, hrv) = data[idx]
                drawLine(Color.White.copy(alpha = 0.25f), Offset(pt.x, 0f), Offset(pt.x, chartH), 1.5f)
                drawCircle(PurpleAccent, 5.dp.toPx(), pt)
                drawCircle(Color.White, 2.5.dp.toPx(), pt)
                drawScrubberTooltip(textMeasurer, "%.0f ms · ${epochSecondsToMonthDay(epoch)}".format(hrv), pt.x, pt.y, chartW)
            }
        }
    }
}
