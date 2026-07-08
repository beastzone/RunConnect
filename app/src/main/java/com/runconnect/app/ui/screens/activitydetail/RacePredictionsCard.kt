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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.sp
import com.runconnect.app.domain.model.RacePrediction
import com.runconnect.app.ui.theme.CardDark
import com.runconnect.app.ui.theme.DividerColor
import com.runconnect.app.ui.theme.TealPrimary
import com.runconnect.app.ui.theme.TextPrimary
import com.runconnect.app.ui.theme.TextSecondary
import com.runconnect.app.utils.FormatUtils

@Composable
fun RacePredictionsCard(
    predictions: List<RacePrediction>,
    modifier: Modifier = Modifier,
) {
    if (predictions.isEmpty()) return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardDark)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Race Predictions",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = TextPrimary,
            )
            Text(
                "Based on this activity via Riegel formula",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
            Spacer(Modifier.height(14.dp))

            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Distance", style = MaterialTheme.typography.labelSmall, color = TextSecondary,
                    modifier = Modifier.weight(1f))
                Text("Predicted", style = MaterialTheme.typography.labelSmall, color = TextSecondary,
                    modifier = Modifier.weight(1f))
                Text("Pace", style = MaterialTheme.typography.labelSmall, color = TextSecondary,
                    modifier = Modifier.weight(1f))
                Text("Best", style = MaterialTheme.typography.labelSmall, color = TextSecondary,
                    modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = DividerColor, thickness = 0.5.dp)

            predictions.forEachIndexed { i, pred ->
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = pred.distanceLabel,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = FormatUtils.formatRaceTime(pred.predictedSeconds),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = TealPrimary,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = FormatUtils.formatPacePerKm(pred.predictedPaceSecondsPerKm),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = pred.personalBestSeconds?.let { FormatUtils.formatRaceTime(it) } ?: "—",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (pred.personalBestSeconds != null &&
                            pred.personalBestSeconds < pred.predictedSeconds)
                            TealPrimary else TextSecondary,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (i < predictions.size - 1) {
                    Spacer(Modifier.height(4.dp))
                    HorizontalDivider(color = DividerColor.copy(alpha = 0.5f), thickness = 0.5.dp)
                }
            }
        }
    }
}
