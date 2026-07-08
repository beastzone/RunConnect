package com.runconnect.app.ui.screens.activities

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.runconnect.app.domain.model.ActivityType
import com.runconnect.app.ui.components.ActivityCard
import com.runconnect.app.ui.theme.Background
import com.runconnect.app.ui.theme.CardDark
import com.runconnect.app.ui.theme.TealPrimary
import com.runconnect.app.ui.theme.TextPrimary
import com.runconnect.app.ui.theme.TextSecondary

@Composable
fun ActivitiesScreen(
    onActivityClick: (String) -> Unit,
    viewModel: ActivitiesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background),
    ) {
        Column(
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Text(
                "Activities",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary,
            )
        }

        // Activity type filter chips
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = state.selectedFilter == null,
                onClick = { viewModel.setFilter(null) },
                label = { Text("All") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = TealPrimary,
                    selectedLabelColor = MaterialTheme.colorScheme.background,
                ),
            )
            ActivityType.values().forEach { type ->
                FilterChip(
                    selected = state.selectedFilter == type,
                    onClick = { viewModel.setFilter(type) },
                    label = { Text(type.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = TealPrimary,
                        selectedLabelColor = MaterialTheme.colorScheme.background,
                    ),
                )
            }
        }

        // Source filter chips (only when activities come from multiple apps)
        if (state.availableSources.size > 1) {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = state.sourceFilter == null,
                    onClick = { viewModel.setSourceFilter(null) },
                    label = { Text("All Sources") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = TealPrimary,
                        selectedLabelColor = MaterialTheme.colorScheme.background,
                        labelColor = TextSecondary,
                    ),
                )
                state.availableSources.forEach { (pkg, displayName) ->
                    FilterChip(
                        selected = state.sourceFilter == pkg,
                        onClick = { viewModel.setSourceFilter(pkg) },
                        label = { Text(displayName) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = TealPrimary,
                            selectedLabelColor = MaterialTheme.colorScheme.background,
                            labelColor = TextSecondary,
                        ),
                    )
                }
            }
        }

        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = TealPrimary, modifier = Modifier.size(32.dp))
            }
            return
        }

        if (state.filteredActivities.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No activities found", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
            }
            return
        }

        LazyColumn(
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(state.filteredActivities, key = { it.id }) { activity ->
                ActivityCard(
                    activity = activity,
                    onClick = { onActivityClick(activity.id) },
                    useImperial = state.useImperial,
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .fillMaxWidth(),
                )
            }
        }
    }
}
