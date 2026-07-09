package com.runconnect.app.ui.screens.sleep

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.runconnect.app.domain.model.SleepSession
import com.runconnect.app.domain.model.SleepStageType
import com.runconnect.app.ui.components.axisLabelStyle
import com.runconnect.app.ui.components.chartScrubber
import com.runconnect.app.ui.components.drawScrubberTooltip
import com.runconnect.app.ui.theme.SleepAwake
import com.runconnect.app.ui.theme.SleepDeep
import com.runconnect.app.ui.theme.SleepLight
import com.runconnect.app.ui.theme.SleepRem
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.shape.RoundedCornerShape

@Composable
fun SleepHypnogram(
    session: SleepSession,
    modifier: Modifier = Modifier,
    onTimeSelected: ((Instant) -> Unit)? = null,
) {
    if (session.stages.isEmpty()) return

    val textMeasurer = rememberTextMeasurer()
    var scrubX by remember { mutableStateOf<Float?>(null) }
    val totalDuration = (session.endTime.epochSecond - session.startTime.epochSecond).toFloat()
    if (totalDuration <= 0) return

    val timeFmt = DateTimeFormatter.ofPattern("h:mma")
    val hrSamples = session.heartRateSamples

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(8.dp))
            .chartScrubber { sx -> scrubX = sx }
    ) {
        val w = size.width
        val h = size.height
        val leftLabelW = 32.dp.toPx()
        val bottomAxisH = 18.dp.toPx()
        val topMargin = 4.dp.toPx()
        val stageAreaH = h - bottomAxisH - topMargin
        val stageAreaTop = topMargin
        val chartW = w - leftLabelW

        // Lane Y fractions (top = awake, bottom = deep)
        fun laneTopFrac(type: SleepStageType): Float = when (type) {
            SleepStageType.AWAKE, SleepStageType.OUT_OF_BED -> 0.00f
            SleepStageType.REM -> 0.22f
            SleepStageType.LIGHT, SleepStageType.SLEEPING_UNSPECIFIED -> 0.47f
            SleepStageType.DEEP -> 0.72f
            SleepStageType.UNKNOWN -> 0.47f
        }
        fun laneBottomFrac(type: SleepStageType): Float = when (type) {
            SleepStageType.AWAKE, SleepStageType.OUT_OF_BED -> 0.22f
            SleepStageType.REM -> 0.47f
            SleepStageType.LIGHT, SleepStageType.SLEEPING_UNSPECIFIED -> 0.72f
            SleepStageType.DEEP -> 0.97f
            SleepStageType.UNKNOWN -> 0.72f
        }
        fun stageColor(type: SleepStageType): Color = when (type) {
            SleepStageType.DEEP -> SleepDeep
            SleepStageType.LIGHT, SleepStageType.SLEEPING_UNSPECIFIED -> SleepLight
            SleepStageType.REM -> SleepRem
            SleepStageType.AWAKE, SleepStageType.OUT_OF_BED -> SleepAwake
            SleepStageType.UNKNOWN -> Color(0xFF2A3242)
        }

        // Stage segments
        session.stages.forEach { stage ->
            val x0 = leftLabelW + ((stage.startTime.epochSecond - session.startTime.epochSecond) / totalDuration) * chartW
            val x1 = leftLabelW + ((stage.endTime.epochSecond - session.startTime.epochSecond) / totalDuration) * chartW
            val yTop = stageAreaTop + stageAreaH * laneTopFrac(stage.type)
            val yBottom = stageAreaTop + stageAreaH * laneBottomFrac(stage.type)
            drawRect(
                color = stageColor(stage.type),
                topLeft = Offset(x0, yTop + 1.dp.toPx()),
                size = Size((x1 - x0).coerceAtLeast(1f), (yBottom - yTop - 2.dp.toPx()).coerceAtLeast(1f)),
            )
        }

        // Left-axis lane labels
        val labelPairs = listOf(
            "Awake" to 0.11f,
            "REM" to 0.345f,
            "Light" to 0.595f,
            "Deep" to 0.845f,
        )
        labelPairs.forEach { (label, frac) ->
            val y = stageAreaTop + stageAreaH * frac
            drawText(
                textMeasurer, label,
                Offset(0f, y - 5.dp.toPx()),
                axisLabelStyle,
            )
        }

        // Time axis — hour tick marks + labels
        val zone = ZoneId.systemDefault()
        val sessionStartSec = session.startTime.epochSecond
        val sessionEndSec = session.endTime.epochSecond
        val firstHourSec = ((sessionStartSec / 3600) + 1) * 3600
        val axisY = stageAreaTop + stageAreaH + 3.dp.toPx()
        var prevLabelX = Float.NEGATIVE_INFINITY
        var t = firstHourSec
        while (t <= sessionEndSec) {
            val x = leftLabelW + ((t - sessionStartSec).toFloat() / totalDuration) * chartW
            drawLine(Color.White.copy(alpha = 0.2f), Offset(x, stageAreaTop + stageAreaH - 4.dp.toPx()), Offset(x, stageAreaTop + stageAreaH), 1.dp.toPx())
            val hourInst = Instant.ofEpochSecond(t)
            val hourOfDay = hourInst.atZone(zone).hour
            val label = when {
                hourOfDay == 0 -> "12a"
                hourOfDay == 12 -> "12p"
                hourOfDay < 12 -> "${hourOfDay}a"
                else -> "${hourOfDay - 12}p"
            }
            val measured = textMeasurer.measure(label, axisLabelStyle)
            val labelX = x - measured.size.width / 2f
            if (labelX > prevLabelX + 36.dp.toPx()) {
                drawText(textMeasurer, label, Offset(labelX, axisY), axisLabelStyle)
                prevLabelX = labelX
            }
            t += 3600
        }

        // Scrubber
        scrubX?.let { sx ->
            val clampedX = sx.coerceIn(leftLabelW, leftLabelW + chartW)
            val t2 = ((clampedX - leftLabelW) / chartW) * totalDuration
            val instant = Instant.ofEpochSecond(sessionStartSec + t2.toLong())
            onTimeSelected?.invoke(instant)
            val stage = session.stages.firstOrNull { instant >= it.startTime && instant <= it.endTime }
            val stageName = stage?.type?.name?.lowercase()?.replaceFirstChar { it.uppercase() }
                ?.replace("_", " ") ?: "–"
            val timeStr = timeFmt.format(instant.atZone(zone))
            val dur = stage?.let { (it.endTime.epochSecond - it.startTime.epochSecond) / 60 }
            val avgHr = if (hrSamples.isNotEmpty() && stage != null) {
                hrSamples.filter { it.first >= stage.startTime && it.first <= stage.endTime }
                    .map { it.second }.average().takeIf { !it.isNaN() }?.toInt()
            } else null
            val tooltip = buildString {
                append("$stageName · $timeStr")
                if (dur != null) append(" · ${dur}m")
                if (avgHr != null) append(" · ${avgHr}bpm")
            }
            drawLine(Color.White.copy(alpha = 0.5f), Offset(clampedX, topMargin), Offset(clampedX, stageAreaTop + stageAreaH), 1.dp.toPx())
            drawScrubberTooltip(textMeasurer, tooltip, clampedX, topMargin + stageAreaH / 2f, w)
        }
    }
}
