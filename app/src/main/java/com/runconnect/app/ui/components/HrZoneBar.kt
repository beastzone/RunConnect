package com.runconnect.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.runconnect.app.domain.model.HeartRateZoneSummary
import com.runconnect.app.domain.model.HrZone
import com.runconnect.app.ui.theme.CardDark
import com.runconnect.app.ui.theme.TextSecondary
import com.runconnect.app.ui.theme.ZoneAerobic
import com.runconnect.app.ui.theme.ZoneEasy
import com.runconnect.app.ui.theme.ZoneMax
import com.runconnect.app.ui.theme.ZoneTempo
import com.runconnect.app.ui.theme.ZoneThreshold

val HrZone.uiColor: Color
    get() = when (this) {
        HrZone.ZONE_1 -> ZoneEasy
        HrZone.ZONE_2 -> ZoneAerobic
        HrZone.ZONE_3 -> ZoneTempo
        HrZone.ZONE_4 -> ZoneThreshold
        HrZone.ZONE_5 -> ZoneMax
    }

@Composable
fun HrZoneBar(summary: HeartRateZoneSummary, showTime: Boolean = true) {
    val zoneColor = summary.zone.uiColor
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            summary.zone.label,
            style = MaterialTheme.typography.labelMedium,
            color = zoneColor,
            modifier = Modifier.width(80.dp),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(CardDark)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(summary.percentOfTotal.coerceIn(0f, 1f))
                    .clip(RoundedCornerShape(4.dp))
                    .background(zoneColor)
            )
        }
        if (showTime) {
            Spacer(Modifier.width(8.dp))
            Text(
                "${summary.totalMinutes}min",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                modifier = Modifier.width(48.dp),
            )
        }
    }
}
