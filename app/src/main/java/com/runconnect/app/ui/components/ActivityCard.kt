package com.runconnect.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.SportsScore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.runconnect.app.domain.model.Activity
import com.runconnect.app.domain.model.ActivityType
import com.runconnect.app.domain.model.averagePaceSecondsPerKm
import com.runconnect.app.domain.model.averagePaceSecondsPerMile
import com.runconnect.app.domain.model.distanceKm
import com.runconnect.app.domain.model.distanceMiles
import com.runconnect.app.ui.theme.AmberAccent
import com.runconnect.app.ui.theme.CardDark
import com.runconnect.app.ui.theme.CoralAccent
import com.runconnect.app.ui.theme.CycleColor
import com.runconnect.app.ui.theme.DividerColor
import com.runconnect.app.ui.theme.HeartRate
import com.runconnect.app.ui.theme.HikeColor
import com.runconnect.app.ui.theme.RunColor
import com.runconnect.app.ui.theme.TealPrimary
import com.runconnect.app.ui.theme.TextPrimary
import com.runconnect.app.ui.theme.TextSecondary
import com.runconnect.app.ui.theme.WalkColor
import com.runconnect.app.utils.FormatUtils

@Composable
fun ActivityCard(
    activity: Activity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    useImperial: Boolean = false,
) {
    val typeColor = activity.type.accentColor
    val typeIcon = activity.type.icon

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(CardDark)
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(typeColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = typeIcon,
                        contentDescription = null,
                        tint = typeColor,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = activity.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = TextPrimary,
                    )
                    Text(
                        text = FormatUtils.formatRelativeDate(activity.startTime, activity.startZoneOffset) +
                                " · " + FormatUtils.formatTime(activity.startTime, activity.startZoneOffset),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
                if (activity.hasDuplicate) {
                    Text(
                        text = "⚠ Dup",
                        style = MaterialTheme.typography.labelSmall,
                        color = AmberAccent,
                        modifier = Modifier.padding(end = 6.dp),
                    )
                }
                if (activity.dataOriginPackage.isNotEmpty()) {
                    Text(
                        text = packageToDisplayName(activity.dataOriginPackage),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        modifier = Modifier
                            .border(0.5.dp, TextSecondary.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = DividerColor, thickness = 0.5.dp)
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                SmallStatItem(
                    label = if (useImperial) "Miles" else "Km",
                    value = if (useImperial) "%.2f".format(activity.distanceMiles)
                    else "%.2f".format(activity.distanceKm),
                )
                SmallStatItem(
                    label = "Time",
                    value = FormatUtils.formatDuration(activity.durationSeconds),
                )
                if (activity.type == ActivityType.RUNNING || activity.type == ActivityType.HIKING) {
                    val paceS = if (useImperial) activity.averagePaceSecondsPerMile
                    else activity.averagePaceSecondsPerKm
                    SmallStatItem(
                        label = if (useImperial) "/mi" else "/km",
                        value = if (paceS > 0) {
                            "%d:%02d".format((paceS / 60).toInt(), (paceS % 60).toInt())
                        } else "--",
                    )
                }
                activity.averageHeartRate?.let { hr ->
                    SmallStatItem(
                        label = "Avg HR",
                        value = "$hr",
                        valueColor = HeartRate,
                    )
                }
            }

            if (activity.completenessScore > 0) {
                Spacer(Modifier.height(10.dp))
                val completenessColor = when {
                    activity.completenessScore >= 80 -> TealPrimary
                    activity.completenessScore >= 50 -> AmberAccent
                    else -> CoralAccent
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(TextSecondary.copy(alpha = 0.1f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(activity.completenessScore / 100f)
                            .fillMaxHeight()
                            .background(completenessColor)
                    )
                }
            }
        }
    }
}

val ActivityType.accentColor: Color
    get() = when (this) {
        ActivityType.RUNNING -> RunColor
        ActivityType.HIKING -> HikeColor
        ActivityType.WALKING -> WalkColor
        ActivityType.CYCLING -> CycleColor
        ActivityType.OTHER -> Color(0xFF8897A6)
    }

val ActivityType.icon: ImageVector
    get() = when (this) {
        ActivityType.RUNNING -> Icons.Filled.DirectionsRun
        ActivityType.HIKING -> Icons.Filled.Landscape
        ActivityType.WALKING -> Icons.Filled.DirectionsWalk
        ActivityType.CYCLING -> Icons.Filled.SportsScore
        ActivityType.OTHER -> Icons.Filled.SportsScore
    }

fun packageToDisplayName(pkg: String): String = when {
    pkg.contains("garmin") -> "Garmin Connect"
    pkg.contains("withings") -> "Withings"
    pkg.contains("google.android.apps.fitness") -> "Google Fit"
    pkg.contains("samsung") && pkg.contains("health") -> "Samsung Health"
    pkg.contains("polar") -> "Polar Flow"
    pkg.contains("wahoo") -> "Wahoo"
    pkg.contains("strava") -> "Strava"
    pkg.isEmpty() -> ""
    else -> pkg.substringAfterLast(".", pkg)
}
