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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.runconnect.app.ui.theme.CardDark
import com.runconnect.app.ui.theme.TealPrimary

/**
 * Generic scatter chart. Each point is (xValue, yValue).
 * xLabel and yLabel are shown on axes.
 */
@Composable
fun HrScatterChart(
    points: List<Pair<Float, Float>>,
    xLabel: String,
    yLabel: String,
    modifier: Modifier = Modifier,
) {
    if (points.isEmpty()) return

    val textMeasurer = rememberTextMeasurer()

    val xMin = points.minOf { it.first }
    val xMax = points.maxOf { it.first }.let { if (it == xMin) xMin + 1f else it }
    val yMin = points.minOf { it.second }
    val yMax = points.maxOf { it.second }.let { if (it == yMin) yMin + 1f else it }
    val xRange = (xMax - xMin).coerceAtLeast(0.01f)
    val yRange = (yMax - yMin).coerceAtLeast(0.01f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(CardDark)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val axisBottomPad = 18.dp.toPx()
            val axisRightPad = 34.dp.toPx()
            val labelTopPad = 8.dp.toPx()
            val chartW = size.width - axisRightPad
            val chartH = size.height - axisBottomPad - labelTopPad
            val chartTop = labelTopPad

            fun xFor(v: Float) = ((v - xMin) / xRange * chartW * 0.9f + chartW * 0.05f)
            fun yFor(v: Float) = chartTop + chartH - ((v - yMin) / yRange * chartH * 0.85f + chartH * 0.075f)

            val dotRadius = 3.dp.toPx()
            val style = axisLabelStyle

            points.forEach { (x, y) ->
                drawCircle(
                    color = TealPrimary.copy(alpha = 0.55f),
                    radius = dotRadius,
                    center = Offset(xFor(x), yFor(y)),
                )
            }

            // X axis label (center bottom)
            val xm = textMeasurer.measure(xLabel, style)
            drawText(
                textMeasurer, xLabel,
                topLeft = Offset(chartW / 2f - xm.size.width / 2f, chartTop + chartH + 4.dp.toPx()),
                style = style,
            )

            // Y axis: 3 labels (min, mid, max)
            val yMid = (yMin + yMax) / 2
            listOf(yMax to yFor(yMax), yMid to yFor(yMid), yMin to yFor(yMin)).forEach { (v, y) ->
                val label = if (v >= 100f) v.toInt().toString() else "%.1f".format(v)
                val m = textMeasurer.measure(label, style)
                drawText(
                    textMeasurer, label,
                    topLeft = Offset(chartW + 3.dp.toPx(), (y - m.size.height / 2f).coerceIn(chartTop, chartTop + chartH)),
                    style = style,
                )
            }

            // Y axis label (rotated text is complex; draw it vertically at far left as letters)
            val ym = textMeasurer.measure(yLabel, style)
            drawText(
                textMeasurer, yLabel,
                topLeft = Offset(0f, chartTop + chartH / 2f - ym.size.height / 2f),
                style = style,
            )

            // Axis lines
            drawLine(
                TealPrimary.copy(alpha = 0.1f),
                Offset(0f, chartTop + chartH),
                Offset(chartW, chartTop + chartH),
                1f,
            )
            drawLine(
                TealPrimary.copy(alpha = 0.1f),
                Offset(0f, chartTop),
                Offset(0f, chartTop + chartH),
                1f,
            )
        }
    }
}
