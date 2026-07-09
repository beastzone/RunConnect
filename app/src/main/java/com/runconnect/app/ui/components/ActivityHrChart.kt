package com.runconnect.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.runconnect.app.domain.analytics.ActivityHrAnalytics
import com.runconnect.app.domain.model.HrZone
import com.runconnect.app.domain.model.LapData
import com.runconnect.app.domain.model.ZoneModel
import com.runconnect.app.ui.theme.AmberAccent
import com.runconnect.app.ui.theme.CardDark
import com.runconnect.app.ui.theme.HeartRate
import com.runconnect.app.ui.theme.ZoneAerobic
import com.runconnect.app.ui.theme.ZoneEasy
import com.runconnect.app.ui.theme.ZoneMax
import com.runconnect.app.ui.theme.ZoneTempo
import com.runconnect.app.ui.theme.ZoneThreshold
import kotlin.math.roundToInt

@Composable
fun ActivityHrChart(
    hrData: List<Pair<Float, Int>>,
    avgHr: Int,
    maxHrSetting: Int,
    activityStartEpoch: Long,
    activityDurationSeconds: Long,
    laps: List<LapData> = emptyList(),
    zoneModel: ZoneModel = ZoneModel.MAX_HR,
    restingHr: Int = 60,
    artifacts: ActivityHrAnalytics.ArtifactSpans = ActivityHrAnalytics.ArtifactSpans.empty,
    modifier: Modifier = Modifier,
) {
    if (hrData.isEmpty()) return

    val textMeasurer = rememberTextMeasurer()
    var scrubberX by remember { mutableStateOf<Float?>(null) }

    val minBpm = (hrData.minOf { it.second } - 5).coerceAtLeast(40)
    val maxBpm = hrData.maxOf { it.second } + 5
    val range = (maxBpm - minBpm).coerceAtLeast(1).toFloat()
    val totalDuration = activityDurationSeconds.toFloat().coerceAtLeast(1f)

    // Zone BPM thresholds for bands
    val zoneBands: List<Pair<HrZone, IntRange>> = when (zoneModel) {
        ZoneModel.MAX_HR -> listOf(
            HrZone.ZONE_1 to (0..(maxHrSetting * 0.60).toInt()),
            HrZone.ZONE_2 to ((maxHrSetting * 0.60).toInt()..(maxHrSetting * 0.70).toInt()),
            HrZone.ZONE_3 to ((maxHrSetting * 0.70).toInt()..(maxHrSetting * 0.80).toInt()),
            HrZone.ZONE_4 to ((maxHrSetting * 0.80).toInt()..(maxHrSetting * 0.90).toInt()),
            HrZone.ZONE_5 to ((maxHrSetting * 0.90).toInt()..300),
        )
        ZoneModel.HRR -> {
            val reserve = (maxHrSetting - restingHr).coerceAtLeast(1)
            listOf(
                HrZone.ZONE_1 to (0..(restingHr + reserve * 0.50).toInt()),
                HrZone.ZONE_2 to ((restingHr + reserve * 0.50).toInt()..(restingHr + reserve * 0.60).toInt()),
                HrZone.ZONE_3 to ((restingHr + reserve * 0.60).toInt()..(restingHr + reserve * 0.70).toInt()),
                HrZone.ZONE_4 to ((restingHr + reserve * 0.70).toInt()..(restingHr + reserve * 0.85).toInt()),
                HrZone.ZONE_5 to ((restingHr + reserve * 0.85).toInt()..300),
            )
        }
    }

    val artifactEpochs: Set<Long> = artifacts.suspectTimestamps

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(CardDark)
    ) {
        Canvas(
            modifier = Modifier.fillMaxSize().chartScrubber { scrubberX = it }
        ) {
            val axisBottomPad = 18.dp.toPx()
            val axisRightPad = 34.dp.toPx()
            val labelTopPad = 12.dp.toPx()
            val chartW = size.width - axisRightPad
            val chartH = size.height - axisBottomPad - labelTopPad
            val chartTop = labelTopPad

            fun xFor(timeOffset: Float) = (timeOffset / totalDuration * chartW).coerceIn(0f, chartW)
            fun yFor(bpm: Int) = chartTop + chartH - ((bpm - minBpm).toFloat() / range * chartH * 0.85f + chartH * 0.075f)

            // --- Zone bands (horizontal colored strips) ---
            zoneBands.forEach { (zone, bpmRange) ->
                val bandMin = bpmRange.first.coerceAtLeast(minBpm)
                val bandMax = bpmRange.last.coerceAtMost(maxBpm)
                if (bandMax <= bandMin) return@forEach
                val y1 = yFor(bandMax)
                val y2 = yFor(bandMin)
                val bandColor = when (zone) {
                    HrZone.ZONE_1 -> ZoneEasy
                    HrZone.ZONE_2 -> ZoneAerobic
                    HrZone.ZONE_3 -> ZoneTempo
                    HrZone.ZONE_4 -> ZoneThreshold
                    HrZone.ZONE_5 -> ZoneMax
                }
                drawRect(
                    color = bandColor.copy(alpha = 0.07f),
                    topLeft = Offset(0f, y1),
                    size = androidx.compose.ui.geometry.Size(chartW, (y2 - y1).coerceAtLeast(0f)),
                )
            }

            // --- Fill path under line ---
            val pts = hrData.map { (t, bpm) -> Offset(xFor(t), yFor(bpm)) }
            val fillPath = Path().apply {
                moveTo(pts.first().x, chartTop + chartH)
                pts.forEach { lineTo(it.x, it.y) }
                lineTo(pts.last().x, chartTop + chartH)
                close()
            }
            drawPath(fillPath, color = HeartRate.copy(alpha = 0.08f))

            // --- Line ---
            val linePath = Path().apply {
                pts.forEachIndexed { i, pt -> if (i == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y) }
            }
            drawPath(linePath, color = HeartRate, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))

            // --- Avg HR dashed reference line ---
            if (avgHr > 0) {
                val avgY = yFor(avgHr)
                drawLine(
                    Color.White.copy(alpha = 0.15f),
                    Offset(0f, avgY),
                    Offset(chartW, avgY),
                    strokeWidth = 1.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 5f)),
                )
            }

            // --- Max HR dot + vertical dotted line ---
            val peakIdx = hrData.indices.maxByOrNull { hrData[it].second } ?: -1
            if (peakIdx >= 0) {
                val peakPt = pts[peakIdx]
                drawLine(
                    HeartRate.copy(alpha = 0.3f),
                    Offset(peakPt.x, chartTop),
                    Offset(peakPt.x, chartTop + chartH),
                    strokeWidth = 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)),
                )
                drawCircle(HeartRate, radius = 5.dp.toPx(), center = peakPt)
                drawCircle(Color.White, radius = 2.5.dp.toPx(), center = peakPt)
            }

            // --- Lap boundary lines ---
            val axisStyle = axisLabelStyle
            laps.forEachIndexed { idx, lap ->
                val lapOffsetSec = (lap.startTime.epochSecond - activityStartEpoch).toFloat()
                if (lapOffsetSec <= 0f || lapOffsetSec >= totalDuration) return@forEachIndexed
                val lx = xFor(lapOffsetSec)
                drawLine(
                    Color.White.copy(alpha = 0.25f),
                    Offset(lx, chartTop),
                    Offset(lx, chartTop + chartH),
                    strokeWidth = 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 3f)),
                )
                val lapLabel = "L${idx + 1}"
                val lm = textMeasurer.measure(lapLabel, axisStyle)
                drawText(
                    textMeasurer, lapLabel,
                    topLeft = Offset((lx + 2f).coerceAtMost(chartW - lm.size.width.toFloat()), chartTop + 2f),
                    style = axisStyle,
                )
            }

            // --- Artifact dots (hollow amber) ---
            pts.forEachIndexed { i, pt ->
                val epoch = activityStartEpoch + (hrData[i].first / 1f).toLong()
                if (epoch in artifactEpochs) {
                    drawCircle(AmberAccent.copy(alpha = 0.7f), radius = 4.dp.toPx(), center = pt, style = Stroke(1.5f))
                }
            }

            // --- Y axis labels ---
            val midBpm = (minBpm + maxBpm) / 2
            listOf(
                yFor(maxBpm) to maxBpm,
                yFor(midBpm) to midBpm,
                yFor(minBpm) to minBpm,
            ).forEach { (y, bpm) ->
                val label = "$bpm"
                val m = textMeasurer.measure(label, axisStyle)
                drawText(
                    textMeasurer, label,
                    topLeft = Offset(chartW + 3.dp.toPx(), (y - m.size.height.toFloat() / 2f).coerceIn(chartTop, chartTop + chartH)),
                    style = axisStyle,
                )
            }

            // --- X axis labels: time offsets (0, mid, end) ---
            fun formatOffset(seconds: Long): String {
                val m = seconds / 60
                return if (m < 60) "${m}m" else "${m / 60}h${"%02d".format(m % 60)}m"
            }
            listOf(0f to 0L, 0.5f to (activityDurationSeconds / 2), 1f to activityDurationSeconds)
                .forEach { (frac, offsetSec) ->
                    val label = formatOffset(offsetSec)
                    val m = textMeasurer.measure(label, axisStyle)
                    val cx = frac * chartW
                    val lx = when {
                        frac == 0f -> cx
                        frac == 1f -> (cx - m.size.width.toFloat()).coerceAtMost(chartW - m.size.width.toFloat())
                        else -> cx - m.size.width.toFloat() / 2f
                    }
                    drawText(textMeasurer, label, topLeft = Offset(lx, chartTop + chartH + 3.dp.toPx()), style = axisStyle)
                }

            // --- Scrubber ---
            scrubberX?.let { sx ->
                val idx = (sx / chartW * (hrData.size - 1)).roundToInt().coerceIn(0, hrData.size - 1)
                val pt = pts[idx]
                val bpm = hrData[idx].second
                val zone = computeHrZoneLabel(bpm, maxHrSetting, zoneModel, restingHr)
                drawLine(Color.White.copy(alpha = 0.25f), Offset(pt.x, chartTop), Offset(pt.x, chartTop + chartH), 1.5f)
                drawCircle(HeartRate, 5.dp.toPx(), pt)
                drawCircle(Color.White, 2.5.dp.toPx(), pt)
                drawScrubberTooltip(textMeasurer, "$bpm bpm · $zone", pt.x, pt.y, chartW)
            }
        }
    }
}

private fun computeHrZoneLabel(bpm: Int, maxHr: Int, model: ZoneModel, restingHr: Int): String {
    val zone = when (model) {
        ZoneModel.MAX_HR -> {
            val pct = bpm.toFloat() / maxHr
            when {
                pct < 0.60f -> "Z1"
                pct < 0.70f -> "Z2"
                pct < 0.80f -> "Z3"
                pct < 0.90f -> "Z4"
                else -> "Z5"
            }
        }
        ZoneModel.HRR -> {
            val reserve = (maxHr - restingHr).coerceAtLeast(1)
            val pct = (bpm - restingHr).toFloat() / reserve
            when {
                pct < 0.50f -> "Z1"
                pct < 0.60f -> "Z2"
                pct < 0.70f -> "Z3"
                pct < 0.85f -> "Z4"
                else -> "Z5"
            }
        }
    }
    return zone
}
