package com.runconnect.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.runconnect.app.domain.model.RoutePoint
import com.runconnect.app.ui.theme.CardDark
import com.runconnect.app.ui.theme.ElevationHigh
import com.runconnect.app.ui.theme.ElevationLow
import com.runconnect.app.ui.theme.ElevationMid
import com.runconnect.app.ui.theme.HeartRate
import com.runconnect.app.ui.theme.TealPrimary
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun ElevationChart(
    routePoints: List<RoutePoint>,
    modifier: Modifier = Modifier,
) {
    val validPoints = routePoints.filter { it.altitudeMeters != null }
    val elevations = validPoints.map { it.altitudeMeters!! }
    if (elevations.size < 2) return

    val minElev = elevations.min()
    val maxElev = elevations.max()
    val elevRange = (maxElev - minElev).coerceAtLeast(1.0)
    val distancesKm = validPoints.cumulativeDistancesKm()
    val totalDistKm = distancesKm.lastOrNull() ?: 1.0

    val textMeasurer = rememberTextMeasurer()
    var scrubberX by remember { mutableStateOf<Float?>(null) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
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
            val innerPad = 8f
            val chartW = size.width - axisRightPad
            val chartH = size.height - axisBottomPad

            fun xFor(i: Int) = innerPad + (i.toFloat() / (elevations.size - 1)) * (chartW - 2 * innerPad)
            fun yFor(elev: Double) = chartH - innerPad - ((elev - minElev) / elevRange * (chartH - 2 * innerPad)).toFloat()

            val points = elevations.mapIndexed { i, e -> Offset(xFor(i), yFor(e)) }

            // Fill
            val fillPath = Path().apply {
                moveTo(points.first().x, chartH)
                points.forEach { lineTo(it.x, it.y) }
                lineTo(points.last().x, chartH)
                close()
            }
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    listOf(ElevationMid.copy(alpha = 0.4f), ElevationLow.copy(alpha = 0.05f)),
                    endY = chartH,
                )
            )

            // Line
            val linePath = Path().apply {
                moveTo(points.first().x, points.first().y)
                for (i in 1 until points.size) {
                    val prev = points[i - 1]; val curr = points[i]
                    val cpx = (prev.x + curr.x) / 2
                    cubicTo(cpx, prev.y, cpx, curr.y, curr.x, curr.y)
                }
            }
            drawPath(
                path = linePath,
                brush = Brush.horizontalGradient(
                    listOf(ElevationLow, ElevationMid, ElevationHigh),
                    endX = chartW,
                ),
                style = Stroke(2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )

            // Y axis labels: max (top), mid, min (bottom)
            val axisStyle = axisLabelStyle
            listOf(
                innerPad to maxElev,
                chartH / 2f to (minElev + maxElev) / 2,
                chartH - innerPad to minElev,
            ).forEach { (y, elev) ->
                val label = "${elev.toInt()} m"
                val m = textMeasurer.measure(label, axisStyle)
                drawText(
                    textMeasurer, label,
                    topLeft = Offset(chartW + 3.dp.toPx(), (y - m.size.height.toFloat() / 2f).coerceAtLeast(0f)),
                    style = axisStyle,
                )
            }

            // X axis labels: 0 km, mid km, total km
            listOf(0f to 0.0, 0.5f to totalDistKm / 2, 1f to totalDistKm).forEach { (frac, distKm) ->
                val label = "%.1f".format(distKm)
                val m = textMeasurer.measure(label, axisStyle)
                val cx = innerPad + frac * (chartW - 2 * innerPad)
                val lx = when {
                    frac == 0f -> cx
                    frac == 1f -> (cx - m.size.width.toFloat()).coerceAtMost(chartW - m.size.width.toFloat())
                    else -> cx - m.size.width.toFloat() / 2f
                }
                drawText(textMeasurer, label, topLeft = Offset(lx, chartH + 3.dp.toPx()), style = axisStyle)
            }

            // Scrubber
            scrubberX?.let { sx ->
                val frac = ((sx - innerPad) / (chartW - 2 * innerPad)).coerceIn(0f, 1f)
                val idx = (frac * (elevations.size - 1)).roundToInt().coerceIn(0, elevations.size - 1)
                val pt = points[idx]
                drawLine(Color.White.copy(alpha = 0.25f), Offset(pt.x, 0f), Offset(pt.x, chartH), 1.5f)
                drawCircle(ElevationMid, 5.dp.toPx(), pt)
                drawCircle(Color.White, 2.5.dp.toPx(), pt)
                val elev = elevations[idx]
                val distKm = distancesKm.getOrElse(idx) { 0.0 }
                drawScrubberTooltip(textMeasurer, "%.0f m · %.1f km".format(elev, distKm), pt.x, pt.y, chartW)
            }
        }
    }
}

@Composable
fun PaceChart(
    paceData: List<Pair<Float, Float>>,  // (timeOffsetSeconds, paceSecondsPerKm)
    avgPaceSeconds: Double,
    modifier: Modifier = Modifier,
    lineColor: Color = TealPrimary,
) {
    if (paceData.size < 2) return

    val minPace = paceData.minOf { it.second }
    val maxPace = paceData.maxOf { it.second }
    val paceRange = (maxPace - minPace).coerceAtLeast(1f)
    val totalDuration = paceData.last().first

    val textMeasurer = rememberTextMeasurer()
    var scrubberX by remember { mutableStateOf<Float?>(null) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(140.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(CardDark)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .chartScrubber { scrubberX = it }
        ) {
            val axisBottomPad = 18.dp.toPx()
            val axisRightPad = 42.dp.toPx()
            val innerPad = 10f
            val chartW = size.width - axisRightPad
            val chartH = size.height - axisBottomPad

            // Inverted: faster pace (lower seconds) = lower y value (higher on screen)
            fun xFor(i: Int) = innerPad + (i.toFloat() / (paceData.size - 1)) * (chartW - 2 * innerPad)
            fun yFor(pace: Float) = innerPad + ((pace - minPace) / paceRange) * (chartH - 2 * innerPad)

            val points = paceData.mapIndexed { i, (_, pace) -> Offset(xFor(i), yFor(pace)) }

            // Avg pace reference line
            val avgY = yFor(avgPaceSeconds.toFloat())
            drawLine(Color.White.copy(alpha = 0.1f), Offset(innerPad, avgY), Offset(chartW - innerPad, avgY), 1f)

            // Fill
            val fillPath = Path().apply {
                moveTo(points.first().x, chartH)
                points.forEach { lineTo(it.x, it.y) }
                lineTo(points.last().x, chartH)
                close()
            }
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    listOf(lineColor.copy(alpha = 0.3f), lineColor.copy(alpha = 0f)),
                    endY = chartH,
                )
            )

            // Line
            val linePath = Path().apply {
                moveTo(points.first().x, points.first().y)
                for (i in 1 until points.size) {
                    val prev = points[i - 1]; val curr = points[i]
                    val cpx = (prev.x + curr.x) / 2
                    cubicTo(cpx, prev.y, cpx, curr.y, curr.x, curr.y)
                }
            }
            drawPath(linePath, lineColor, style = Stroke(2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))

            // Y axis labels: top=fastest(minPace), bottom=slowest(maxPace)
            val axisStyle = axisLabelStyle
            val midPace = (minPace + maxPace) / 2f
            listOf(
                innerPad to minPace,
                chartH / 2f to midPace,
                chartH - innerPad to maxPace,
            ).forEach { (y, pace) ->
                val label = formatPaceMin(pace)
                val m = textMeasurer.measure(label, axisStyle)
                drawText(
                    textMeasurer, label,
                    topLeft = Offset(chartW + 3.dp.toPx(), (y - m.size.height.toFloat() / 2f).coerceAtLeast(0f)),
                    style = axisStyle,
                )
            }

            // X axis labels: elapsed time
            listOf(0f to 0f, 0.5f to totalDuration / 2, 1f to totalDuration).forEach { (frac, elapsed) ->
                val label = formatElapsed(elapsed)
                val m = textMeasurer.measure(label, axisStyle)
                val cx = innerPad + frac * (chartW - 2 * innerPad)
                val lx = when {
                    frac == 0f -> cx
                    frac == 1f -> (cx - m.size.width.toFloat()).coerceAtMost(chartW - m.size.width.toFloat())
                    else -> cx - m.size.width.toFloat() / 2f
                }
                drawText(textMeasurer, label, topLeft = Offset(lx, chartH + 3.dp.toPx()), style = axisStyle)
            }

            // Scrubber
            scrubberX?.let { sx ->
                val frac = ((sx - innerPad) / (chartW - 2 * innerPad)).coerceIn(0f, 1f)
                val idx = (frac * (paceData.size - 1)).roundToInt().coerceIn(0, paceData.size - 1)
                val pt = points[idx]
                drawLine(Color.White.copy(alpha = 0.25f), Offset(pt.x, 0f), Offset(pt.x, chartH), 1.5f)
                drawCircle(lineColor, 5.dp.toPx(), pt)
                drawCircle(Color.White, 2.5.dp.toPx(), pt)
                drawScrubberTooltip(textMeasurer, formatPaceFull(paceData[idx].second), pt.x, pt.y, chartW)
            }
        }
    }
}

@Composable
fun HeartRateChart(
    hrData: List<Pair<Float, Int>>,  // (timeOffsetSeconds, bpm)
    avgHr: Int,
    maxHr: Int = 190,
    modifier: Modifier = Modifier,
) {
    if (hrData.size < 2) return

    val minBpm = hrData.minOf { it.second }.toFloat()
    val maxBpm = hrData.maxOf { it.second }.toFloat()
    val bpmRange = (maxBpm - minBpm).coerceAtLeast(1f)
    val totalDuration = hrData.last().first
    val lineColor = HeartRate

    val textMeasurer = rememberTextMeasurer()
    var scrubberX by remember { mutableStateOf<Float?>(null) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(140.dp)
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
            val innerPad = 10f
            val chartW = size.width - axisRightPad
            val chartH = size.height - axisBottomPad

            fun xFor(i: Int) = innerPad + (i.toFloat() / (hrData.size - 1)) * (chartW - 2 * innerPad)
            fun yFor(bpm: Float) = chartH - innerPad - ((bpm - minBpm) / bpmRange) * (chartH - 2 * innerPad)

            val points = hrData.mapIndexed { i, (_, bpm) -> Offset(xFor(i), yFor(bpm.toFloat())) }

            // Fill
            val fillPath = Path().apply {
                moveTo(points.first().x, chartH)
                points.forEach { lineTo(it.x, it.y) }
                lineTo(points.last().x, chartH)
                close()
            }
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    listOf(lineColor.copy(alpha = 0.3f), lineColor.copy(alpha = 0f)),
                    endY = chartH,
                )
            )

            // Line
            val linePath = Path().apply {
                moveTo(points.first().x, points.first().y)
                for (i in 1 until points.size) {
                    val prev = points[i - 1]; val curr = points[i]
                    val cpx = (prev.x + curr.x) / 2
                    cubicTo(cpx, prev.y, cpx, curr.y, curr.x, curr.y)
                }
            }
            drawPath(linePath, lineColor, style = Stroke(2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))

            // Y axis labels: top=maxBpm, bottom=minBpm
            val axisStyle = axisLabelStyle
            val midBpm = (minBpm + maxBpm) / 2f
            listOf(
                innerPad to maxBpm,
                chartH / 2f to midBpm,
                chartH - innerPad to minBpm,
            ).forEach { (y, bpm) ->
                val label = "${bpm.toInt()}"
                val m = textMeasurer.measure(label, axisStyle)
                drawText(
                    textMeasurer, label,
                    topLeft = Offset(chartW + 3.dp.toPx(), (y - m.size.height.toFloat() / 2f).coerceAtLeast(0f)),
                    style = axisStyle,
                )
            }

            // X axis labels: elapsed time
            listOf(0f to 0f, 0.5f to totalDuration / 2, 1f to totalDuration).forEach { (frac, elapsed) ->
                val label = formatElapsed(elapsed)
                val m = textMeasurer.measure(label, axisStyle)
                val cx = innerPad + frac * (chartW - 2 * innerPad)
                val lx = when {
                    frac == 0f -> cx
                    frac == 1f -> (cx - m.size.width.toFloat()).coerceAtMost(chartW - m.size.width.toFloat())
                    else -> cx - m.size.width.toFloat() / 2f
                }
                drawText(textMeasurer, label, topLeft = Offset(lx, chartH + 3.dp.toPx()), style = axisStyle)
            }

            // Scrubber
            scrubberX?.let { sx ->
                val frac = ((sx - innerPad) / (chartW - 2 * innerPad)).coerceIn(0f, 1f)
                val idx = (frac * (hrData.size - 1)).roundToInt().coerceIn(0, hrData.size - 1)
                val pt = points[idx]
                drawLine(Color.White.copy(alpha = 0.25f), Offset(pt.x, 0f), Offset(pt.x, chartH), 1.5f)
                drawCircle(lineColor, 5.dp.toPx(), pt)
                drawCircle(Color.White, 2.5.dp.toPx(), pt)
                drawScrubberTooltip(textMeasurer, "${hrData[idx].second} bpm", pt.x, pt.y, chartW)
            }
        }
    }
}

private fun formatPaceMin(secondsPerKm: Float): String {
    val s = secondsPerKm.toInt()
    return "${s / 60}:%02d".format(s % 60)
}

private fun formatPaceFull(secondsPerKm: Float): String {
    val s = secondsPerKm.toInt()
    return "${s / 60}:%02d /km".format(s % 60)
}

private fun formatElapsed(totalSeconds: Float): String {
    val s = totalSeconds.toInt()
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
    else "%d:%02d".format(s / 60, s % 60)
}

private fun List<RoutePoint>.cumulativeDistancesKm(): List<Double> {
    if (size < 2) return List(size) { 0.0 }
    val result = mutableListOf(0.0)
    for (i in 1 until size) {
        val p = this[i - 1]; val c = this[i]
        val dLat = Math.toRadians(c.latitude - p.latitude)
        val dLon = Math.toRadians(c.longitude - p.longitude)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(p.latitude)) * cos(Math.toRadians(c.latitude)) * sin(dLon / 2).pow(2)
        result.add(result.last() + 6371.0 * 2 * atan2(sqrt(a), sqrt(1.0 - a)))
    }
    return result
}
