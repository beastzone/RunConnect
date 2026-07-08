package com.runconnect.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.runconnect.app.domain.model.RoutePoint
import com.runconnect.app.ui.theme.CardDark
import com.runconnect.app.ui.theme.ElevationHigh
import com.runconnect.app.ui.theme.ElevationLow
import com.runconnect.app.ui.theme.ElevationMid

@Composable
fun ElevationChart(
    routePoints: List<RoutePoint>,
    modifier: Modifier = Modifier,
) {
    val elevations = routePoints.mapNotNull { it.altitudeMeters }
    if (elevations.size < 2) return

    val minElevation = elevations.min()
    val maxElevation = elevations.max()
    val elevationRange = (maxElevation - minElevation).coerceAtLeast(1.0)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(100.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(CardDark)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val padding = 8f

            val points = elevations.mapIndexed { i, elev ->
                val x = padding + (i.toFloat() / (elevations.size - 1)) * (width - 2 * padding)
                val y = height - padding - ((elev - minElevation) / elevationRange * (height - 2 * padding)).toFloat()
                Offset(x, y)
            }

            // Fill path
            val fillPath = Path().apply {
                moveTo(points.first().x, height)
                points.forEach { lineTo(it.x, it.y) }
                lineTo(points.last().x, height)
                close()
            }
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        ElevationMid.copy(alpha = 0.4f),
                        ElevationLow.copy(alpha = 0.05f),
                    )
                )
            )

            // Line path
            val linePath = Path().apply {
                moveTo(points.first().x, points.first().y)
                for (i in 1 until points.size) {
                    val prev = points[i - 1]
                    val curr = points[i]
                    val cpx = (prev.x + curr.x) / 2
                    cubicTo(cpx, prev.y, cpx, curr.y, curr.x, curr.y)
                }
            }
            drawPath(
                path = linePath,
                brush = Brush.horizontalGradient(
                    colors = listOf(ElevationLow, ElevationMid, ElevationHigh)
                ),
                style = Stroke(
                    width = 2.5f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                )
            )
        }
    }
}

@Composable
fun PaceChart(
    paceData: List<Pair<Float, Float>>,  // (distanceKm, paceSecondsPerKm)
    avgPaceSeconds: Double,
    modifier: Modifier = Modifier,
    lineColor: Color = com.runconnect.app.ui.theme.TealPrimary,
) {
    if (paceData.size < 2) return

    val minPace = paceData.minOf { it.second }
    val maxPace = paceData.maxOf { it.second }
    val paceRange = (maxPace - minPace).coerceAtLeast(1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(CardDark)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val padding = 10f

            val points = paceData.mapIndexed { i, (_, pace) ->
                val x = padding + (i.toFloat() / (paceData.size - 1)) * (width - 2 * padding)
                // Inverted: faster pace (lower seconds) = higher on chart
                val y = padding + ((pace - minPace) / paceRange) * (height - 2 * padding)
                Offset(x, y)
            }

            // Avg pace line
            val avgY = padding + ((avgPaceSeconds.toFloat() - minPace) / paceRange) * (height - 2 * padding)
            drawLine(
                color = Color.White.copy(alpha = 0.1f),
                start = Offset(padding, avgY),
                end = Offset(width - padding, avgY),
                strokeWidth = 1f,
            )

            // Fill
            val fillPath = Path().apply {
                moveTo(points.first().x, height)
                points.forEach { lineTo(it.x, it.y) }
                lineTo(points.last().x, height)
                close()
            }
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(lineColor.copy(alpha = 0.3f), lineColor.copy(alpha = 0.0f))
                )
            )

            // Line
            val linePath = Path().apply {
                moveTo(points.first().x, points.first().y)
                for (i in 1 until points.size) {
                    val prev = points[i - 1]
                    val curr = points[i]
                    val cpx = (prev.x + curr.x) / 2
                    cubicTo(cpx, prev.y, cpx, curr.y, curr.x, curr.y)
                }
            }
            drawPath(
                path = linePath,
                color = lineColor,
                style = Stroke(width = 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
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

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(CardDark)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val padding = 10f

            val points = hrData.mapIndexed { i, (_, bpm) ->
                val x = padding + (i.toFloat() / (hrData.size - 1)) * (width - 2 * padding)
                val y = height - padding - ((bpm - minBpm) / bpmRange) * (height - 2 * padding)
                Offset(x, y)
            }

            val fillPath = Path().apply {
                moveTo(points.first().x, height)
                points.forEach { lineTo(it.x, it.y) }
                lineTo(points.last().x, height)
                close()
            }
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        com.runconnect.app.ui.theme.HeartRate.copy(alpha = 0.3f),
                        com.runconnect.app.ui.theme.HeartRate.copy(alpha = 0.0f),
                    )
                )
            )

            val linePath = Path().apply {
                moveTo(points.first().x, points.first().y)
                for (i in 1 until points.size) {
                    val prev = points[i - 1]
                    val curr = points[i]
                    val cpx = (prev.x + curr.x) / 2
                    cubicTo(cpx, prev.y, cpx, curr.y, curr.x, curr.y)
                }
            }
            drawPath(
                path = linePath,
                color = com.runconnect.app.ui.theme.HeartRate,
                style = Stroke(width = 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }
}
