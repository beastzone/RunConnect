package com.runconnect.app.ui.screens.bodymetrics

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
import com.runconnect.app.domain.model.BodyMetricsSample
import com.runconnect.app.ui.components.SectionHeader
import com.runconnect.app.ui.components.SmallStatItem
import com.runconnect.app.ui.theme.AmberAccent
import com.runconnect.app.ui.theme.Background
import com.runconnect.app.ui.theme.CardDark
import com.runconnect.app.ui.theme.CoralAccent
import com.runconnect.app.ui.theme.TealPrimary
import com.runconnect.app.ui.theme.TextPrimary
import com.runconnect.app.ui.theme.TextSecondary
import com.runconnect.app.utils.FormatUtils
import java.time.ZoneId

@Composable
fun BodyMetricsScreen(viewModel: BodyMetricsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize().background(Background)) {
        Column(modifier = Modifier.statusBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(
                "Body Metrics",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary,
            )
            Text("Last 90 days from Health Connect", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }

        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = TealPrimary, modifier = Modifier.size(32.dp))
            }
            return
        }

        if (state.samples.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No body metrics found.\nSyncing Withings to Health Connect will show weight and body fat here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
            return
        }

        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
            // Current stats card
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
                        state.latestWeightKg?.let { kg ->
                            val display = if (state.useImperial) "%.1f lbs".format(kg * 2.20462) else "%.1f kg".format(kg)
                            val change = state.weightChangeKg?.let {
                                val sign = if (it >= 0) "+" else ""
                                if (state.useImperial) " ($sign%.1f lbs)".format(it * 2.20462)
                                else " ($sign%.1f kg)".format(it)
                            }
                            SmallStatItem(
                                label = "Weight",
                                value = display + (change ?: ""),
                                valueColor = if ((state.weightChangeKg ?: 0.0) <= 0) TealPrimary else CoralAccent,
                            )
                        }
                        state.latestBodyFatPct?.let { fat ->
                            val change = state.bodyFatChangePct?.let {
                                val sign = if (it >= 0) "+" else ""
                                " ($sign%.1f%%)".format(it)
                            }
                            SmallStatItem(
                                label = "Body Fat",
                                value = "%.1f%%".format(fat) + (change ?: ""),
                                valueColor = if ((state.bodyFatChangePct ?: 0.0) <= 0) TealPrimary else AmberAccent,
                            )
                        }
                    }
                }
            }

            // Weight trend chart
            val weightSamples = state.samples.filter { it.weightKg != null }
            if (weightSamples.size >= 2) {
                item {
                    Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
                        SectionHeader("Weight Trend")
                        Spacer(Modifier.height(10.dp))
                        MetricTrendChart(
                            samples = weightSamples,
                            getValue = { it.weightKg!! * if (state.useImperial) 2.20462 else 1.0 },
                            color = TealPrimary,
                            modifier = Modifier.fillMaxWidth().height(120.dp),
                        )
                    }
                }
            }

            // Body fat trend chart
            val fatSamples = state.samples.filter { it.bodyFatPercent != null }
            if (fatSamples.size >= 2) {
                item {
                    Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
                        SectionHeader("Body Fat % Trend")
                        Spacer(Modifier.height(10.dp))
                        MetricTrendChart(
                            samples = fatSamples,
                            getValue = { it.bodyFatPercent!! },
                            color = AmberAccent,
                            modifier = Modifier.fillMaxWidth().height(120.dp),
                        )
                    }
                }
            }

            // Recent entries table
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    SectionHeader("Recent Measurements")
                    Spacer(Modifier.height(10.dp))
                    state.samples.take(15).forEach { sample ->
                        BodyMetricsRow(sample = sample, useImperial = state.useImperial)
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricTrendChart(
    samples: List<BodyMetricsSample>,
    getValue: (BodyMetricsSample) -> Double,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val values = samples.map { getValue(it) }
    val minVal = values.minOrNull() ?: return
    val maxVal = values.maxOrNull() ?: return
    val range = (maxVal - minVal).coerceAtLeast(0.1)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val points = values.reversed().mapIndexed { i, v ->
            val x = (i.toFloat() / (values.size - 1)) * w
            val y = h - ((v - minVal) / range * h * 0.8f + h * 0.1f).toFloat()
            Offset(x, y)
        }

        // Fill area under line
        val fillPath = Path().apply {
            moveTo(points.first().x, h)
            points.forEach { lineTo(it.x, it.y) }
            lineTo(points.last().x, h)
            close()
        }
        drawPath(fillPath, color = color.copy(alpha = 0.08f))

        // Draw line
        val linePath = Path().apply {
            points.forEachIndexed { i, pt -> if (i == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y) }
        }
        drawPath(linePath, color = color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))

        // Draw dots for first and last
        drawCircle(color = color, radius = 4.dp.toPx(), center = points.first())
        drawCircle(color = color, radius = 4.dp.toPx(), center = points.last())
    }
}

@Composable
private fun BodyMetricsRow(sample: BodyMetricsSample, useImperial: Boolean) {
    val date = sample.timestamp.atZone(ZoneId.systemDefault()).toLocalDate()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(CardDark)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                FormatUtils.formatShortDate(date),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                sample.weightKg?.let { kg ->
                    Text(
                        if (useImperial) "%.1f lbs".format(kg * 2.20462) else "%.1f kg".format(kg),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = TealPrimary,
                    )
                }
                sample.bodyFatPercent?.let { fat ->
                    Text(
                        "%.1f%%".format(fat),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = AmberAccent,
                    )
                }
            }
        }
    }
}
