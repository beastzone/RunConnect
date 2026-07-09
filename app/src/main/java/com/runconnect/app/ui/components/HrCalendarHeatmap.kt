package com.runconnect.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.runconnect.app.ui.theme.CardDark
import com.runconnect.app.ui.theme.CoralAccent
import com.runconnect.app.ui.theme.TealPrimary
import java.time.DayOfWeek
import java.time.LocalDate

private val HeatmapNoDataColor = Color(0xFF1E2733)

/**
 * 90-day resting-HR calendar heatmap (13 weeks × 7 days).
 * Color: TealPrimary (low RHR = good) → CoralAccent (high RHR = elevated).
 * Tap a cell to see its date and RHR value.
 */
@Composable
fun HrCalendarHeatmap(
    data: List<Pair<LocalDate, Int>>,
    modifier: Modifier = Modifier,
) {
    val dataMap = remember(data) { data.toMap() }
    val minBpm = remember(data) { data.minOfOrNull { it.second } ?: 40 }
    val maxBpm = remember(data) { (data.maxOfOrNull { it.second } ?: 80).coerceAtLeast(minBpm + 1) }
    val bpmRange = (maxBpm - minBpm).toFloat()

    val today = remember { LocalDate.now() }
    val currentWeekMonday = remember(today) { today.with(DayOfWeek.MONDAY) }
    val gridStart = remember(currentWeekMonday) { currentWeekMonday.minusWeeks(12) }

    val textMeasurer = rememberTextMeasurer()
    var tappedCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(CardDark)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val dayLabelW = 20.dp.toPx()
                        val monthLabelH = 18.dp.toPx()
                        if (offset.x > dayLabelW && offset.y > monthLabelH) {
                            val cellAreaW = size.width - dayLabelW
                            val cellAreaH = size.height - monthLabelH
                            val col = ((offset.x - dayLabelW) / (cellAreaW / 13f)).toInt().coerceIn(0, 12)
                            val row = ((offset.y - monthLabelH) / (cellAreaH / 7f)).toInt().coerceIn(0, 6)
                            val date = gridStart.plusDays((col * 7L + row))
                            tappedCell = if (!date.isAfter(today)) Pair(col, row) else null
                        } else {
                            tappedCell = null
                        }
                    }
                }
        ) {
            val dayLabelW = 20.dp.toPx()
            val monthLabelH = 18.dp.toPx()
            val gapPx = 2.dp.toPx()
            val cols = 13
            val rows = 7
            val cellAreaW = size.width - dayLabelW
            val cellAreaH = size.height - monthLabelH
            val cellW = (cellAreaW - gapPx * cols) / cols
            val cellH = (cellAreaH - gapPx * rows) / rows
            val cornerR = CornerRadius(2.dp.toPx())
            val axisStyle = axisLabelStyle

            // Month labels — show short name at the first column where the month changes
            var lastMonth = gridStart.month
            for (col in 0 until cols) {
                val weekDate = gridStart.plusWeeks(col.toLong())
                if (col == 0 || weekDate.month != lastMonth) {
                    lastMonth = weekDate.month
                    val label = weekDate.month.name.take(3)
                        .lowercase()
                        .replaceFirstChar { it.uppercase() }
                    val x = dayLabelW + col * (cellW + gapPx)
                    drawText(textMeasurer, label, topLeft = Offset(x, 0f), style = axisStyle)
                }
            }

            // Day labels: M, W, F on left side
            listOf(0 to "M", 2 to "W", 4 to "F").forEach { (row, label) ->
                val y = monthLabelH + row * (cellH + gapPx) + cellH / 2f - axisStyle.fontSize.toPx() / 2f
                drawText(textMeasurer, label, topLeft = Offset(0f, y), style = axisStyle)
            }

            // Cells
            for (col in 0 until cols) {
                for (row in 0 until rows) {
                    val date = gridStart.plusDays((col * 7L + row))
                    if (date.isAfter(today)) continue

                    val bpm = dataMap[date]
                    val cellColor = if (bpm != null) {
                        val frac = ((bpm - minBpm).toFloat() / bpmRange).coerceIn(0f, 1f)
                        lerp(TealPrimary, CoralAccent, frac).copy(alpha = 0.85f)
                    } else {
                        HeatmapNoDataColor
                    }

                    val isTapped = tappedCell == Pair(col, row)
                    val x = dayLabelW + col * (cellW + gapPx)
                    val y = monthLabelH + row * (cellH + gapPx)

                    drawRoundRect(
                        color = if (isTapped) Color.White.copy(alpha = 0.9f) else cellColor,
                        topLeft = Offset(x, y),
                        size = Size(cellW, cellH),
                        cornerRadius = cornerR,
                    )
                }
            }

            // Tooltip for tapped cell
            tappedCell?.let { (col, row) ->
                val date = gridStart.plusDays((col * 7L + row))
                val bpm = dataMap[date]
                val monthName = date.month.name.take(3)
                    .lowercase()
                    .replaceFirstChar { it.uppercase() }
                val label = if (bpm != null) "$monthName ${date.dayOfMonth}: $bpm bpm"
                else "$monthName ${date.dayOfMonth}: No data"
                val tx = dayLabelW + col * (cellW + gapPx) + cellW / 2f
                val ty = monthLabelH + row * (cellH + gapPx)
                drawScrubberTooltip(textMeasurer, label, tx, ty, size.width)
            }
        }
    }
}
