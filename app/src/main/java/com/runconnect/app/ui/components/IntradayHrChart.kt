package com.runconnect.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.runconnect.app.domain.model.HeartRateSample
import com.runconnect.app.ui.theme.BlueAccent
import com.runconnect.app.ui.theme.CardDark
import com.runconnect.app.ui.theme.TealPrimary
import com.runconnect.app.ui.theme.TextSecondary
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

private val hourFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private const val GAP_SECONDS = 300L   // 5-minute gap = lift the pen

@Composable
fun IntradayHrChart(
    samples: List<HeartRateSample>,
    sleepWindows: List<Pair<Instant, Instant>> = emptyList(),
    date: LocalDate,
    modifier: Modifier = Modifier,
) {
    val zone = ZoneId.systemDefault()
    val dayStartEpoch = date.atStartOfDay(zone).toInstant().epochSecond

    if (samples.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(CardDark),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "No heart rate data for this day",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
        }
        return
    }

    val bpms = samples.map { it.bpm.toInt() }
    val minBpm = ((bpms.minOrNull() ?: 40) - 5).coerceAtLeast(30)
    val maxBpm = ((bpms.maxOrNull() ?: 180) + 5).coerceAtMost(220)
    val bpmRange = (maxBpm - minBpm).coerceAtLeast(1).toFloat()

    val textMeasurer = rememberTextMeasurer()
    var scrubberX by remember(date) { mutableStateOf<Float?>(null) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
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

            fun xForEpoch(epoch: Long): Float =
                ((epoch - dayStartEpoch).toFloat() / 86400f * chartW).coerceIn(0f, chartW)

            fun yForBpm(bpm: Int): Float =
                chartH - ((bpm - minBpm).toFloat() / bpmRange * chartH * 0.8f + chartH * 0.1f)

            // Sleep bands drawn first (behind the HR line)
            for ((sleepStart, sleepEnd) in sleepWindows) {
                val sx = xForEpoch(sleepStart.epochSecond)
                val ex = xForEpoch(sleepEnd.epochSecond)
                if (ex > sx) {
                    drawRect(
                        color = BlueAccent.copy(alpha = 0.15f),
                        topLeft = Offset(sx, 0f),
                        size = Size(ex - sx, chartH),
                    )
                }
            }

            // HR line with gap detection — lift pen for gaps > 5 min
            val path = Path()
            samples.forEachIndexed { i, sample ->
                val x = xForEpoch(sample.timestamp.epochSecond)
                val y = yForBpm(sample.bpm.toInt())
                when {
                    i == 0 -> path.moveTo(x, y)
                    sample.timestamp.epochSecond - samples[i - 1].timestamp.epochSecond > GAP_SECONDS ->
                        path.moveTo(x, y)
                    else -> path.lineTo(x, y)
                }
            }
            drawPath(path, color = TealPrimary, style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round))

            // Y-axis labels
            val axisStyle = axisLabelStyle
            val midBpm = (minBpm + maxBpm) / 2
            listOf(0.1f * chartH to maxBpm, 0.5f * chartH to midBpm, 0.9f * chartH to minBpm)
                .forEach { (y, bpm) ->
                    val label = "$bpm"
                    val m = textMeasurer.measure(label, axisStyle)
                    drawText(
                        textMeasurer, label,
                        topLeft = Offset(chartW + 3.dp.toPx(), (y - m.size.height / 2f).coerceAtLeast(0f)),
                        style = axisStyle,
                    )
                }

            // X-axis labels: 00:00, 06:00, 12:00, 18:00, 00:00
            listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { frac ->
                val epoch = dayStartEpoch + (frac * 86400f).toLong()
                val label = Instant.ofEpochSecond(epoch).atZone(zone).format(hourFmt)
                val m = textMeasurer.measure(label, axisStyle)
                val cx = frac * chartW
                val lx = when {
                    frac == 0f -> cx
                    frac == 1f -> (cx - m.size.width).coerceAtMost(chartW - m.size.width)
                    else -> cx - m.size.width / 2f
                }
                drawText(textMeasurer, label, topLeft = Offset(lx, chartH + 3.dp.toPx()), style = axisStyle)
            }

            // Scrubber — binary search for nearest sample (O(log n) for dense data)
            scrubberX?.let { sx ->
                val epochAtScrub = dayStartEpoch + (sx / chartW * 86400f).toLong()
                var lo = 0; var hi = samples.lastIndex
                while (lo < hi) {
                    val mid = (lo + hi) / 2
                    if (samples[mid].timestamp.epochSecond < epochAtScrub) lo = mid + 1 else hi = mid
                }
                val idx = if (lo > 0 &&
                    abs(samples[lo - 1].timestamp.epochSecond - epochAtScrub) <
                    abs(samples[lo].timestamp.epochSecond - epochAtScrub)
                ) lo - 1 else lo
                val nearest = samples[idx]
                val px = xForEpoch(nearest.timestamp.epochSecond)
                val py = yForBpm(nearest.bpm.toInt())
                val timeLabel = Instant.ofEpochSecond(nearest.timestamp.epochSecond).atZone(zone).format(hourFmt)
                drawLine(Color.White.copy(alpha = 0.25f), Offset(px, 0f), Offset(px, chartH), 1.5f)
                drawCircle(TealPrimary, 5.dp.toPx(), Offset(px, py))
                drawCircle(Color.White, 2.5.dp.toPx(), Offset(px, py))
                drawScrubberTooltip(textMeasurer, "${nearest.bpm} bpm · $timeLabel", px, py, chartW)
            }
        }
    }
}
