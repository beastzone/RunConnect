package com.runconnect.app.ui.screens.sleep

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.runconnect.app.ui.components.axisLabelStyle
import com.runconnect.app.ui.components.chartScrubber
import com.runconnect.app.ui.components.drawScrubberTooltip
import com.runconnect.app.ui.theme.AmberAccent
import com.runconnect.app.ui.theme.CardDark
import com.runconnect.app.ui.theme.CoralAccent
import com.runconnect.app.ui.theme.TealPrimary
import com.runconnect.app.ui.theme.TextPrimary
import com.runconnect.app.ui.theme.TextSecondary
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun SleepScoreChart(
    scores: List<DailyScore>,
    selectedDate: LocalDate,
    range: ChartRange,
    onDateSelected: (LocalDate) -> Unit,
    onRangeSelected: (ChartRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    var scrubX by remember { mutableStateOf<Float?>(null) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardDark)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            "Sleep Score Trend",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = TextPrimary,
        )

        // Range selector row
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ChartRange.entries.forEach { r ->
                val label = when (r) {
                    ChartRange.SEVEN_DAYS -> "7D"
                    ChartRange.FOURTEEN_DAYS -> "14D"
                    ChartRange.THIRTY_DAYS -> "30D"
                    ChartRange.THREE_MONTHS -> "3M"
                    ChartRange.SIX_MONTHS -> "6M"
                    ChartRange.ONE_YEAR -> "1Y"
                }
                TextButton(
                    onClick = { onRangeSelected(r) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (r == range) FontWeight.Bold else FontWeight.Normal,
                        ),
                        color = if (r == range) TealPrimary else TextSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .chartScrubber { sx ->
                    scrubX = sx
                    if (sx != null && scores.isNotEmpty()) {
                        // Map scrub X to nearest date index
                    }
                }
        ) {
            if (scores.isEmpty()) return@Canvas

            val leftPad = 28.dp.toPx()
            val bottomPad = 18.dp.toPx()
            val topPad = 4.dp.toPx()
            val chartW = size.width - leftPad
            val chartH = size.height - bottomPad - topPad

            // Score zone backgrounds (score 0–100 maps to chart Y)
            fun scoreToY(s: Int) = topPad + chartH * (1f - s / 100f)

            val zones = listOf(
                0 to 60 to CoralAccent,
                60 to 70 to AmberAccent,
                70 to 80 to Color(0xFFFBBF24),
                80 to 90 to Color(0xFF4ADE80),
                90 to 100 to TealPrimary,
            )
            zones.forEach { (range, color) ->
                val (lo, hi) = range
                drawRect(
                    color = color.copy(alpha = 0.06f),
                    topLeft = Offset(leftPad, scoreToY(hi)),
                    size = Size(chartW, scoreToY(lo) - scoreToY(hi)),
                )
            }

            // Horizontal grid lines
            listOf(25, 50, 75).forEach { g ->
                val y = scoreToY(g)
                drawLine(Color.White.copy(alpha = 0.04f), Offset(leftPad, y), Offset(size.width, y), 0.5.dp.toPx())
            }

            // Y axis labels
            listOf(100, 50, 0).forEach { v ->
                val y = scoreToY(v)
                drawText(textMeasurer, "$v", Offset(0f, y - 5.dp.toPx()), axisLabelStyle)
            }

            // Bars
            val count = scores.size
            val slotW = chartW / count
            val barW = (slotW * 0.65f).coerceAtLeast(2.dp.toPx())
            val cornerR = CornerRadius(2.dp.toPx())

            scores.forEachIndexed { i, daily ->
                val cx = leftPad + slotW * i + slotW / 2f
                val barLeft = cx - barW / 2f
                val isSelected = daily.date == selectedDate

                if (daily.score != null) {
                    val barTop = scoreToY(daily.score)
                    val barHeight = chartH - (barTop - topPad)
                    val color = when {
                        isSelected -> TealPrimary
                        daily.score >= 90 -> TealPrimary.copy(alpha = 0.7f)
                        daily.score >= 80 -> Color(0xFF4ADE80).copy(alpha = 0.7f)
                        daily.score >= 70 -> Color(0xFFFBBF24).copy(alpha = 0.7f)
                        daily.score >= 60 -> AmberAccent.copy(alpha = 0.7f)
                        else -> CoralAccent.copy(alpha = 0.7f)
                    }
                    drawRoundRect(color = color, topLeft = Offset(barLeft, barTop), size = Size(barW, barHeight), cornerRadius = cornerR)
                } else {
                    // No data: outline rect
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.1f),
                        topLeft = Offset(barLeft, topPad + chartH * 0.3f),
                        size = Size(barW, chartH * 0.7f),
                        cornerRadius = cornerR,
                        style = Stroke(0.5.dp.toPx()),
                    )
                }
            }

            // X axis labels
            val dayFmt = DateTimeFormatter.ofPattern("EEE")
            val dateFmt = DateTimeFormatter.ofPattern("MMM d")
            val monthFmt = DateTimeFormatter.ofPattern("MMM")
            val labelEvery = when {
                count <= 7 -> 1
                count <= 14 -> 2
                count <= 31 -> 5
                count <= 90 -> 14
                else -> 30
            }
            scores.forEachIndexed { i, daily ->
                if (i % labelEvery == 0) {
                    val label = when {
                        count <= 7 -> daily.date.format(dayFmt).take(2)
                        count <= 31 -> daily.date.format(dateFmt)
                        else -> daily.date.format(monthFmt)
                    }
                    val cx = leftPad + slotW * i + slotW / 2f
                    val measured = textMeasurer.measure(label, axisLabelStyle)
                    val labelX = (cx - measured.size.width / 2f).coerceIn(leftPad, size.width - measured.size.width)
                    drawText(textMeasurer, label, Offset(labelX, topPad + chartH + 4.dp.toPx()), axisLabelStyle)
                }
            }

            // Scrubber
            scrubX?.let { sx ->
                val adjustedX = (sx - leftPad).coerceIn(0f, chartW)
                val i = ((adjustedX / chartW) * count).toInt().coerceIn(0, count - 1)
                val daily = scores[i]
                val cx = leftPad + slotW * i + slotW / 2f
                drawLine(Color.White.copy(alpha = 0.3f), Offset(cx, topPad), Offset(cx, topPad + chartH), 1.dp.toPx())
                val tooltip = if (daily.score != null) {
                    "${daily.date.format(dateFmt)} · ${daily.score}"
                } else {
                    "${daily.date.format(dateFmt)} · –"
                }
                drawScrubberTooltip(textMeasurer, tooltip, cx, topPad + chartH / 2f, chartW + leftPad)
                // Notify date selection on lift — we can't do it here since Canvas is immediate;
                // the chartScrubber null callback handles lift-up in the outer lambda.
            }
        }
    }
}
