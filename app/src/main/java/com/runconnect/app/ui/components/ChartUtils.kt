package com.runconnect.app.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal val axisLabelStyle = TextStyle(color = Color(0xFF6B7A8D), fontSize = 9.sp)

fun Modifier.chartScrubber(onScrub: (Float?) -> Unit): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            onScrub(down.position.x)
            while (true) {
                val event = awaitPointerEvent()
                if (event.changes.none { it.pressed }) break
                event.changes.firstOrNull()?.also { onScrub(it.position.x) }
            }
            onScrub(null)
        }
    }

fun DrawScope.drawScrubberTooltip(
    textMeasurer: TextMeasurer,
    text: String,
    scrubX: Float,
    scrubY: Float,
    chartWidth: Float,
) {
    val style = TextStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Medium)
    val measured = textMeasurer.measure(text, style)
    val pad = 5.dp.toPx()
    val w = measured.size.width + pad * 2
    val h = measured.size.height + pad * 2
    val minX = 2.dp.toPx()
    val maxX = (chartWidth - w - 2.dp.toPx()).coerceAtLeast(minX)
    val x = (scrubX - w / 2f).coerceIn(minX, maxX)
    val y = (scrubY - h - 8.dp.toPx()).coerceAtLeast(2.dp.toPx())
    drawRoundRect(
        color = Color(0xEE101418),
        topLeft = Offset(x, y),
        size = Size(w, h),
        cornerRadius = CornerRadius(4.dp.toPx()),
    )
    drawText(textMeasurer, text, topLeft = Offset(x + pad, y + pad), style = style)
}

fun epochSecondsToMonthDay(epochSeconds: Long): String =
    Instant.ofEpochSecond(epochSeconds)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("MMM d"))
