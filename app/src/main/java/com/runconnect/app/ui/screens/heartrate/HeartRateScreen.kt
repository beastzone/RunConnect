package com.runconnect.app.ui.screens.heartrate

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.runconnect.app.domain.model.HeartRateZoneSummary
import com.runconnect.app.domain.model.HrZone
import com.runconnect.app.ui.components.SectionHeader
import com.runconnect.app.ui.components.SmallStatItem
import com.runconnect.app.ui.theme.Background
import com.runconnect.app.ui.theme.BlueAccent
import com.runconnect.app.ui.theme.CardDark
import com.runconnect.app.ui.theme.HeartRate
import com.runconnect.app.ui.theme.TealPrimary
import com.runconnect.app.ui.theme.TextPrimary
import com.runconnect.app.ui.theme.TextSecondary
import com.runconnect.app.ui.theme.PurpleAccent
import com.runconnect.app.ui.theme.ZoneAerobic
import com.runconnect.app.ui.theme.ZoneEasy
import com.runconnect.app.ui.theme.ZoneMax
import com.runconnect.app.ui.theme.ZoneTempo
import com.runconnect.app.ui.theme.ZoneThreshold

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
            Text("Last 30 days", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }

        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = TealPrimary, modifier = Modifier.size(32.dp))
            }
            return
        }

        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
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
                            value = "${state.maxRecordedHr} bpm",
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

            // Resting HR trend (from dedicated RestingHeartRateRecord)
            if (state.restingHrHistory.size >= 3) {
                item {
                    Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
                        SectionHeader("Resting HR Trend (30 days)")
                        Spacer(Modifier.height(10.dp))
                        RestingHrChart(
                            data = state.restingHrHistory,
                            modifier = Modifier.fillMaxWidth().height(110.dp),
                        )
                    }
                }
            }

            // HRV trend (from dedicated HeartRateVariabilityRmssdRecord)
            if (state.hrvHistory.size >= 3) {
                item {
                    Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
                        SectionHeader("HRV Trend (30 days)")
                        Spacer(Modifier.height(10.dp))
                        HrvChart(
                            data = state.hrvHistory,
                            modifier = Modifier.fillMaxWidth().height(110.dp),
                        )
                    }
                }
            }

            // HR zones
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
private fun RestingHrChart(data: List<Pair<Long, Int>>, modifier: Modifier = Modifier) {
    val bpms = data.map { it.second }
    val minBpm = (bpms.minOrNull() ?: 40) - 5
    val maxBpm = (bpms.maxOrNull() ?: 80) + 5
    val range = (maxBpm - minBpm).coerceAtLeast(1).toFloat()

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val pts = data.mapIndexed { i, (_, bpm) ->
            val x = (i.toFloat() / (data.size - 1)) * w
            val y = h - ((bpm - minBpm) / range * h * 0.8f + h * 0.1f)
            Offset(x, y)
        }

        val fillPath = Path().apply {
            moveTo(pts.first().x, h)
            pts.forEach { lineTo(it.x, it.y) }
            lineTo(pts.last().x, h)
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
    }
}

@Composable
private fun HrZoneBar(summary: HeartRateZoneSummary) {
    val zoneColor = summary.zone.uiColor
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            summary.zone.label,
            style = MaterialTheme.typography.labelMedium,
            color = zoneColor,
            modifier = Modifier.width(80.dp),
        )
        Box(
            modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(CardDark)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(summary.percentOfTotal.coerceIn(0f, 1f))
                    .clip(RoundedCornerShape(4.dp))
                    .background(zoneColor)
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            "${summary.totalMinutes}min",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            modifier = Modifier.width(48.dp),
        )
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

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val pts = values.mapIndexed { i, v ->
            val x = (i.toFloat() / (values.size - 1)) * w
            val y = h - (((v - minVal) / range * h * 0.8f + h * 0.1f).toFloat())
            Offset(x, y)
        }

        val fillPath = Path().apply {
            moveTo(pts.first().x, h)
            pts.forEach { lineTo(it.x, it.y) }
            lineTo(pts.last().x, h)
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
    }
}

val HrZone.uiColor: Color
    get() = when (this) {
        HrZone.ZONE_1 -> ZoneEasy
        HrZone.ZONE_2 -> ZoneAerobic
        HrZone.ZONE_3 -> ZoneTempo
        HrZone.ZONE_4 -> ZoneThreshold
        HrZone.ZONE_5 -> ZoneMax
    }
