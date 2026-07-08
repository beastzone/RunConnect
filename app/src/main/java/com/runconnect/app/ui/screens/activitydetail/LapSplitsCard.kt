package com.runconnect.app.ui.screens.activitydetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.runconnect.app.domain.model.LapData
import com.runconnect.app.ui.theme.CardDark
import com.runconnect.app.ui.theme.DividerColor
import com.runconnect.app.ui.theme.HeartRate
import com.runconnect.app.ui.theme.TealPrimary
import com.runconnect.app.ui.theme.TextPrimary
import com.runconnect.app.ui.theme.TextSecondary
import com.runconnect.app.utils.FormatUtils

@Composable
fun LapSplitsCard(
    laps: List<LapData>,
    useImperial: Boolean = false,
    modifier: Modifier = Modifier,
) {
    if (laps.isEmpty()) return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardDark)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Lap Splits",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = TextPrimary,
            )
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Lap", style = MaterialTheme.typography.labelSmall, color = TextSecondary,
                    modifier = Modifier.weight(0.5f))
                Text("Dist", style = MaterialTheme.typography.labelSmall, color = TextSecondary,
                    modifier = Modifier.weight(1f))
                Text("Time", style = MaterialTheme.typography.labelSmall, color = TextSecondary,
                    modifier = Modifier.weight(1f))
                Text("Pace", style = MaterialTheme.typography.labelSmall, color = TextSecondary,
                    modifier = Modifier.weight(1f))
                Text("HR", style = MaterialTheme.typography.labelSmall, color = TextSecondary,
                    modifier = Modifier.weight(0.8f))
            }
            Spacer(Modifier.height(6.dp))
            HorizontalDivider(color = DividerColor, thickness = 0.5.dp)

            laps.forEach { lap ->
                Spacer(Modifier.height(10.dp))
                val paceS = lap.paceSecondsPerKm
                val paceDisplay = if (paceS > 0)
                    "%d:%02d".format((paceS / 60).toInt(), (paceS % 60).toInt())
                else "--"

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${lap.lapNumber}",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = TealPrimary,
                        modifier = Modifier.weight(0.5f),
                    )
                    Text(
                        if (useImperial) "%.2f mi".format(lap.distanceMeters / 1609.344)
                        else "%.2f km".format(lap.distanceMeters / 1000.0),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        FormatUtils.formatDuration(lap.durationSeconds),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        paceDisplay + if (useImperial) " /mi" else " /km",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        lap.averageHeartRate?.toString() ?: "—",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (lap.averageHeartRate != null) HeartRate else TextSecondary,
                        modifier = Modifier.weight(0.8f),
                    )
                }
                Spacer(Modifier.height(4.dp))
                HorizontalDivider(color = DividerColor.copy(alpha = 0.4f), thickness = 0.5.dp)
            }
        }
    }
}
