package com.runconnect.app.ui.screens.sleep

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runconnect.app.domain.model.SleepAnnotation
import com.runconnect.app.domain.model.SleepFactorTag
import com.runconnect.app.domain.model.SleepSession
import com.runconnect.app.domain.model.SleepStageType
import com.runconnect.app.ui.components.chartScrubber
import com.runconnect.app.ui.components.drawScrubberTooltip
import com.runconnect.app.ui.theme.AmberAccent
import com.runconnect.app.ui.theme.CardDark
import com.runconnect.app.ui.theme.CoralAccent
import com.runconnect.app.ui.theme.DividerColor
import com.runconnect.app.ui.theme.SleepAwake
import com.runconnect.app.ui.theme.SleepDeep
import com.runconnect.app.ui.theme.SleepLight
import com.runconnect.app.ui.theme.SleepRem
import com.runconnect.app.ui.theme.TealPrimary
import com.runconnect.app.ui.theme.TextPrimary
import com.runconnect.app.ui.theme.TextSecondary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private object ChartUtils {
    val axisLabelStyle = TextStyle(color = Color(0xFF6B7A8D), fontSize = 9.sp)
}

@Composable
fun SleepStageTimeline(session: SleepSession, modifier: Modifier = Modifier) {
    val textMeasurer = rememberTextMeasurer()
    var scrubX by remember { mutableStateOf<Float?>(null) }
    val totalDuration = (session.endTime.epochSecond - session.startTime.epochSecond).toFloat()
    val timeFmt = DateTimeFormatter.ofPattern("h:mm a")
    val hrSamples = session.heartRateSamples

    if (totalDuration <= 0) return

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(8.dp))
            .chartScrubber { scrubX = it }
    ) {
        val w = size.width
        val h = size.height
        val stageH = if (hrSamples.isNotEmpty()) h * 0.65f else h

        // Stage bands
        session.stages.forEach { stage ->
            val x0 = ((stage.startTime.epochSecond - session.startTime.epochSecond) / totalDuration) * w
            val x1 = ((stage.endTime.epochSecond - session.startTime.epochSecond) / totalDuration) * w
            val color = when (stage.type) {
                SleepStageType.DEEP -> SleepDeep
                SleepStageType.LIGHT -> SleepLight
                SleepStageType.REM -> SleepRem
                SleepStageType.AWAKE -> SleepAwake
                SleepStageType.UNKNOWN -> Color(0xFF2A3242)
            }
            drawRect(color = color, topLeft = Offset(x0, 0f), size = androidx.compose.ui.geometry.Size(x1 - x0, stageH))
        }

        // HR overlay line
        if (hrSamples.size >= 2) {
            val minHr = hrSamples.minOf { it.second }.toFloat()
            val maxHr = hrSamples.maxOf { it.second }.toFloat()
            val hrRange = (maxHr - minHr).coerceAtLeast(1f)
            val lineTop = stageH + 2.dp.toPx()
            val lineH = h - lineTop

            val path = Path()
            hrSamples.forEachIndexed { i, (time, bpm) ->
                val x = ((time.epochSecond - session.startTime.epochSecond) / totalDuration) * w
                val y = lineTop + lineH * (1f - (bpm - minHr) / hrRange)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color = Color(0xFFFF6B6B), style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round))
        }

        // Scrubber
        scrubX?.let { sx ->
            val t = (sx / w) * totalDuration
            val instant = Instant.ofEpochSecond(session.startTime.epochSecond + t.toLong())
            val stage = session.stages.firstOrNull { stage ->
                instant >= stage.startTime && instant <= stage.endTime
            }
            val stageName = stage?.type?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "–"
            val timeStr = timeFmt.format(instant.atZone(ZoneId.systemDefault()))
            val hr = hrSamples.minByOrNull { kotlin.math.abs(it.first.epochSecond - instant.epochSecond) }?.second
            val tooltip = if (hr != null) "$stageName · $timeStr · ${hr}bpm" else "$stageName · $timeStr"

            drawLine(Color.White.copy(alpha = 0.6f), Offset(sx, 0f), Offset(sx, h), strokeWidth = 1.dp.toPx())
            drawScrubberTooltip(textMeasurer, tooltip, sx, h / 2f, w)
        }
    }
}

@Composable
fun OvernightLineChart(
    samples: List<Pair<Instant, Double>>,
    title: String,
    unit: String,
    lineColor: Color,
    thresholdValue: Double? = null,
    modifier: Modifier = Modifier,
) {
    if (samples.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(CardDark)
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                Spacer(Modifier.height(4.dp))
                Text("No data available from your device", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
        return
    }

    val textMeasurer = rememberTextMeasurer()
    var scrubX by remember { mutableStateOf<Float?>(null) }

    val minVal = samples.minOf { it.second }
    val maxVal = samples.maxOf { it.second }
    val range = (maxVal - minVal).coerceAtLeast(1.0)
    val axisBottomPad = 18.dp
    val axisRightPad = 34.dp

    Column(modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CardDark).padding(16.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = TextPrimary)
        Spacer(Modifier.height(8.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .chartScrubber { scrubX = it }
        ) {
            val w = size.width - axisRightPad.toPx()
            val h = size.height - axisBottomPad.toPx()
            val startEpoch = samples.first().first.epochSecond
            val endEpoch = samples.last().first.epochSecond
            val timeRange = (endEpoch - startEpoch).toFloat().coerceAtLeast(1f)

            // Threshold line
            thresholdValue?.let { tv ->
                val ty = h * (1f - (tv - minVal) / range).toFloat()
                drawLine(
                    color = CoralAccent.copy(alpha = 0.5f),
                    start = Offset(0f, ty),
                    end = Offset(w, ty),
                    strokeWidth = 1.dp.toPx(),
                )
                drawText(
                    textMeasurer = textMeasurer,
                    text = "${tv.toInt()}",
                    topLeft = Offset(w + 2.dp.toPx(), ty - 6.dp.toPx()),
                    style = ChartUtils.axisLabelStyle.copy(color = CoralAccent),
                )
            }

            // Y-axis labels
            listOf(minVal, (minVal + maxVal) / 2, maxVal).forEachIndexed { i, v ->
                val y = h * (1f - (v - minVal) / range).toFloat()
                drawText(textMeasurer, "${v.toInt()}", Offset(w + 2.dp.toPx(), y - 5.dp.toPx()), ChartUtils.axisLabelStyle)
            }

            // Line
            val path = Path()
            samples.forEachIndexed { i, (time, value) ->
                val x = ((time.epochSecond - startEpoch) / timeRange) * w
                val y = h * (1f - (value - minVal) / range).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color = lineColor, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))

            // X-axis labels (start and end)
            val timeFmt = DateTimeFormatter.ofPattern("h:mm a")
            drawText(textMeasurer, timeFmt.format(Instant.ofEpochSecond(startEpoch).atZone(ZoneId.systemDefault())),
                Offset(0f, h + 4.dp.toPx()), ChartUtils.axisLabelStyle)
            val endLabel = timeFmt.format(Instant.ofEpochSecond(endEpoch).atZone(ZoneId.systemDefault()))
            val endMeasured = textMeasurer.measure(endLabel, ChartUtils.axisLabelStyle)
            drawText(textMeasurer, endLabel,
                Offset((w - endMeasured.size.width).coerceAtLeast(0f), h + 4.dp.toPx()), ChartUtils.axisLabelStyle)

            // Scrubber
            scrubX?.let { sx ->
                val clampedX = sx.coerceIn(0f, w)
                val t = (clampedX / w) * timeRange
                val nearest = samples.minByOrNull { kotlin.math.abs(it.first.epochSecond - (startEpoch + t.toLong())) }
                if (nearest != null) {
                    val nx = ((nearest.first.epochSecond - startEpoch) / timeRange) * w
                    val ny = h * (1f - (nearest.second - minVal) / range).toFloat()
                    drawLine(Color.White.copy(alpha = 0.4f), Offset(nx, 0f), Offset(nx, h), 1.dp.toPx())
                    drawCircle(lineColor, 3.dp.toPx(), Offset(nx, ny))
                    val label = "${"%.1f".format(nearest.second)} $unit"
                    drawScrubberTooltip(textMeasurer, label, nx, ny, w)
                }
            }
        }
    }
}

@Composable
fun StageDetailSection(
    session: SleepSession,
    historicalAvgDeep: Long,
    historicalAvgLight: Long,
    historicalAvgRem: Long,
    modifier: Modifier = Modifier,
) {
    val totalMinutes = session.totalDurationMinutes.coerceAtLeast(1L)

    data class StageRow(val name: String, val color: Color, val minutes: Long, val avgMinutes: Long)

    val rows = listOf(
        StageRow("Deep", SleepDeep, session.deepSleepMinutes, historicalAvgDeep),
        StageRow("Light", SleepLight, session.lightSleepMinutes, historicalAvgLight),
        StageRow("REM", SleepRem, session.remSleepMinutes, historicalAvgRem),
        StageRow("Awake", SleepAwake, session.awakeMinutes, 0L),
    )

    val episodeCounts = mapOf(
        "Deep" to session.stages.count { it.type == SleepStageType.DEEP },
        "Light" to session.stages.count { it.type == SleepStageType.LIGHT },
        "REM" to session.stages.count { it.type == SleepStageType.REM },
        "Awake" to session.stages.count { it.type == SleepStageType.AWAKE },
    )

    val longestEpisodes = mapOf(
        "Deep" to (session.stages.filter { it.type == SleepStageType.DEEP }.maxOfOrNull { (it.endTime.epochSecond - it.startTime.epochSecond) / 60 } ?: 0L),
        "Light" to (session.stages.filter { it.type == SleepStageType.LIGHT }.maxOfOrNull { (it.endTime.epochSecond - it.startTime.epochSecond) / 60 } ?: 0L),
        "REM" to (session.stages.filter { it.type == SleepStageType.REM }.maxOfOrNull { (it.endTime.epochSecond - it.startTime.epochSecond) / 60 } ?: 0L),
        "Awake" to (session.stages.filter { it.type == SleepStageType.AWAKE }.maxOfOrNull { (it.endTime.epochSecond - it.startTime.epochSecond) / 60 } ?: 0L),
    )

    Column(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            Text("Stage", modifier = Modifier.weight(1.2f), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Text("Time", modifier = Modifier.weight(0.8f), style = MaterialTheme.typography.labelSmall, color = TextSecondary, textAlign = TextAlign.End)
            Text("%", modifier = Modifier.weight(0.5f), style = MaterialTheme.typography.labelSmall, color = TextSecondary, textAlign = TextAlign.End)
            Text("Episodes", modifier = Modifier.weight(0.8f), style = MaterialTheme.typography.labelSmall, color = TextSecondary, textAlign = TextAlign.End)
            Text("Longest", modifier = Modifier.weight(0.8f), style = MaterialTheme.typography.labelSmall, color = TextSecondary, textAlign = TextAlign.End)
        }
        HorizontalDivider(color = DividerColor, thickness = 0.5.dp, modifier = Modifier.padding(bottom = 6.dp))

        rows.forEach { row ->
            val pct = (row.minutes * 100 / totalMinutes).toInt()
            val delta = if (row.avgMinutes > 0) row.minutes - row.avgMinutes else null
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(modifier = Modifier.weight(1.2f), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(row.color))
                    Spacer(Modifier.width(6.dp))
                    Text(row.name, style = MaterialTheme.typography.labelSmall, color = TextPrimary)
                }
                Text(
                    "${row.minutes}m",
                    modifier = Modifier.weight(0.8f),
                    style = MaterialTheme.typography.labelSmall,
                    color = row.color,
                    textAlign = TextAlign.End,
                )
                Text("$pct%", modifier = Modifier.weight(0.5f), style = MaterialTheme.typography.labelSmall, color = TextSecondary, textAlign = TextAlign.End)
                Text(
                    "${episodeCounts[row.name] ?: 0}",
                    modifier = Modifier.weight(0.8f),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    textAlign = TextAlign.End,
                )
                Text(
                    "${longestEpisodes[row.name] ?: 0}m",
                    modifier = Modifier.weight(0.8f),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    textAlign = TextAlign.End,
                )
            }
            if (delta != null && row.avgMinutes > 0) {
                val deltaColor = if (row.name == "Awake") {
                    if (delta < 0) TealPrimary else CoralAccent
                } else {
                    if (delta >= 0) TealPrimary else CoralAccent
                }
                Text(
                    "  vs avg: ${if (delta >= 0) "+" else ""}${delta}m",
                    style = MaterialTheme.typography.labelSmall,
                    color = deltaColor,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SleepFactorTagGrid(
    selectedTags: List<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SleepFactorTag.values().forEach { tag ->
            val selected = selectedTags.contains(tag.key)
            FilterChip(
                selected = selected,
                onClick = { onToggle(tag.key) },
                label = { Text(tag.label, style = MaterialTheme.typography.labelSmall) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = TealPrimary.copy(alpha = 0.2f),
                    selectedLabelColor = TealPrimary,
                    labelColor = TextSecondary,
                ),
            )
        }
    }
}

@Composable
fun SleepEnvironmentNotesForm(
    annotation: SleepAnnotation,
    onSave: (SleepAnnotation) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        OutlinedTextField(
            value = annotation.notes,
            onValueChange = { onSave(annotation.copy(notes = it)) },
            label = { Text("Notes", style = MaterialTheme.typography.labelSmall) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = TealPrimary,
                unfocusedBorderColor = DividerColor,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedLabelColor = TealPrimary,
                unfocusedLabelColor = TextSecondary,
                cursorColor = TealPrimary,
            ),
        )
        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = annotation.roomTemp,
            onValueChange = { onSave(annotation.copy(roomTemp = it)) },
            label = { Text("Room Temperature (e.g. 68°F)", style = MaterialTheme.typography.labelSmall) },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = TealPrimary,
                unfocusedBorderColor = DividerColor,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedLabelColor = TealPrimary,
                unfocusedLabelColor = TextSecondary,
                cursorColor = TealPrimary,
            ),
        )
        Spacer(Modifier.height(10.dp))

        Text("Noise Level", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Silent", "Low", "Medium", "High").forEach { level ->
                FilterChip(
                    selected = annotation.noiseLevel == level,
                    onClick = { onSave(annotation.copy(noiseLevel = if (annotation.noiseLevel == level) "" else level)) },
                    label = { Text(level, style = MaterialTheme.typography.labelSmall) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = TealPrimary.copy(alpha = 0.2f),
                        selectedLabelColor = TealPrimary,
                        labelColor = TextSecondary,
                    ),
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        Text("Light Level", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Dark", "Dim", "Light").forEach { level ->
                FilterChip(
                    selected = annotation.lightLevel == level,
                    onClick = { onSave(annotation.copy(lightLevel = if (annotation.lightLevel == level) "" else level)) },
                    label = { Text(level, style = MaterialTheme.typography.labelSmall) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = TealPrimary.copy(alpha = 0.2f),
                        selectedLabelColor = TealPrimary,
                        labelColor = TextSecondary,
                    ),
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Traveling / Away from Home", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
            Switch(
                checked = annotation.isTravel,
                onCheckedChange = { onSave(annotation.copy(isTravel = it)) },
                colors = SwitchDefaults.colors(checkedThumbColor = TealPrimary, checkedTrackColor = TealPrimary.copy(alpha = 0.4f)),
            )
        }
    }
}
