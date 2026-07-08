package com.runconnect.app.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.runconnect.app.ui.components.ActivityCard
import com.runconnect.app.ui.components.SectionHeader
import com.runconnect.app.ui.components.StatCard
import com.runconnect.app.ui.theme.AmberAccent
import com.runconnect.app.ui.theme.Background
import com.runconnect.app.ui.theme.CardDark
import com.runconnect.app.ui.theme.TealPrimary
import com.runconnect.app.ui.theme.TextPrimary
import com.runconnect.app.ui.theme.TextSecondary
import com.runconnect.app.utils.FormatUtils

@Composable
fun DashboardScreen(
    onActivityClick: (String) -> Unit,
    onSeeAllActivities: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Background),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            Column(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Text(
                    text = "RunConnect",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = TealPrimary,
                    )
                )
                Text(
                    text = "Your performance hub",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
        }

        if (state.isLoading) {
            item {
                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = TealPrimary, modifier = Modifier.size(32.dp))
                }
            }
            return@LazyColumn
        }

        state.error?.let { error ->
            item {
                ErrorCard(error, modifier = Modifier.padding(horizontal = 20.dp))
            }
        }

        // Weekly summary cards
        item {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                SectionHeader("This Week")
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    StatCard(
                        label = "Distance",
                        value = if (state.useImperial) "%.1f".format(state.weeklyDistanceKm * 0.621371)
                        else "%.1f".format(state.weeklyDistanceKm),
                        unit = if (state.useImperial) "mi" else "km",
                        icon = Icons.Filled.Straighten,
                        accentColor = TealPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    StatCard(
                        label = "Activities",
                        value = "${state.weeklyActivityCount}",
                        icon = Icons.Filled.DirectionsRun,
                        accentColor = com.runconnect.app.ui.theme.CoralAccent,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    StatCard(
                        label = "Time",
                        value = FormatUtils.formatDuration(state.weeklyTimeSeconds),
                        icon = Icons.Filled.Schedule,
                        accentColor = com.runconnect.app.ui.theme.BlueAccent,
                        modifier = Modifier.weight(1f),
                    )
                    StatCard(
                        label = "Day Streak",
                        value = "${state.streakDays}",
                        unit = if (state.streakDays == 1) "day" else "days",
                        icon = Icons.Filled.LocalFireDepartment,
                        accentColor = AmberAccent,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // Recent activities
        item {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {
                SectionHeader(
                    title = "Recent Activities",
                    trailing = {
                        TextButton(onClick = onSeeAllActivities) {
                            Text("See All", color = TealPrimary, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                )
            }
        }

        if (state.recentActivities.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(CardDark)
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No activities found.\nMake sure Garmin is syncing to Health Connect.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }

        items(state.recentActivities, key = { it.id }) { activity ->
            ActivityCard(
                activity = activity,
                onClick = { onActivityClick(activity.id) },
                useImperial = state.useImperial,
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 10.dp)
                    .fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ErrorCard(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(com.runconnect.app.ui.theme.ErrorContainer)
            .padding(16.dp)
    ) {
        Text(message, color = com.runconnect.app.ui.theme.ErrorColor, style = MaterialTheme.typography.bodyMedium)
    }
}
