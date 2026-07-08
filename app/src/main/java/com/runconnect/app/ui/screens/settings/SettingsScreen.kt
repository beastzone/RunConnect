package com.runconnect.app.ui.screens.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.hilt.navigation.compose.hiltViewModel
import com.runconnect.app.ui.theme.Background
import com.runconnect.app.ui.theme.BorderColor
import com.runconnect.app.ui.theme.CardDark
import com.runconnect.app.ui.theme.CoralAccent
import com.runconnect.app.ui.theme.TealPrimary
import com.runconnect.app.ui.theme.TextPrimary
import com.runconnect.app.ui.theme.TextSecondary

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Refresh permissions whenever the screen resumes — catches the case where
    // the user went to the HC app manually to grant permissions and came back.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshPermissions()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Official documented contract from Health Connect SDK.
    // Requires the manifest to have both:
    //   - ACTION_SHOW_PERMISSIONS_RATIONALE (Android 13-)
    //   - VIEW_PERMISSION_USAGE + HEALTH_PERMISSIONS activity-alias (Android 14+)
    // Without the Android 14+ activity-alias, HC silently refuses to show the dialog.
    val permissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        viewModel.refreshPermissions()
    }

    var garminKey by remember { mutableStateOf("") }
    var garminSecret by remember { mutableStateOf("") }
    var maxHrText by remember { mutableStateOf(state.maxHeartRate.toString()) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Background),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        item {
            Column(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Text(
                    "Settings",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary,
                )
            }
        }

        // --- Display settings ---
        item {
            SettingsSection("Display") {
                SettingsRow(
                    label = "Use Imperial Units (mi)",
                    description = "Show miles, lbs instead of km, kg",
                ) {
                    Switch(
                        checked = state.useImperial,
                        onCheckedChange = { viewModel.setUseImperial(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = TealPrimary, checkedTrackColor = TealPrimary.copy(alpha = 0.4f)),
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = maxHrText,
                    onValueChange = {
                        maxHrText = it
                        it.toIntOrNull()?.let { hr -> viewModel.setMaxHeartRate(hr) }
                    },
                    label = { Text("Max Heart Rate (bpm)", color = TextSecondary) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    colors = textFieldColors(),
                )
            }
        }

        // --- Health Connect ---
        item {
            SettingsSection("Health Connect") {
                StatusRow(
                    label = "SDK Status",
                    value = state.healthConnectStatusLabel,
                    valueColor = if (state.healthConnectAvailable) TealPrimary else CoralAccent,
                )
                Spacer(Modifier.height(4.dp))
                StatusRow(
                    label = "Permissions",
                    value = if (state.healthConnectPermissionsGranted) "All granted"
                            else "${state.healthConnectGrantedCount}/${state.healthConnectRequiredCount} granted",
                    valueColor = if (state.healthConnectPermissionsGranted) TealPrimary else CoralAccent,
                )
                Spacer(Modifier.height(4.dp))
                StatusRow(
                    label = "Last Synced",
                    value = state.lastSyncLabel,
                    valueColor = TextSecondary,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (state.isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = TealPrimary,
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Syncing…", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    } else {
                        OutlinedButton(
                            onClick = { viewModel.syncNow() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TealPrimary),
                            border = androidx.compose.foundation.BorderStroke(1.dp, TealPrimary),
                        ) {
                            Text("Sync Now")
                        }
                    }
                }

                if (!state.healthConnectPermissionsGranted) {
                    Spacer(Modifier.height(12.dp))

                    Button(
                        onClick = {
                            try {
                                permissionLauncher.launch(viewModel.requiredPermissions)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Could not open dialog (${e.message}) — use button below", Toast.LENGTH_LONG).show()
                                openHealthConnectPermissionsPage(context)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Grant Permissions", color = MaterialTheme.colorScheme.background)
                    }

                    Spacer(Modifier.height(8.dp))

                    // Direct fallback: always available, goes to RunConnect's page in HC app
                    OutlinedButton(
                        onClick = { openHealthConnectPermissionsPage(context) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TealPrimary),
                        border = androidx.compose.foundation.BorderStroke(1.dp, TealPrimary),
                    ) {
                        Text("Open Health Connect App")
                    }

                    Spacer(Modifier.height(6.dp))
                    Text(
                        when (state.healthConnectSdkStatus) {
                            HealthConnectClient.SDK_UNAVAILABLE ->
                                "Health Connect is not installed. Install it from the Play Store, then tap Grant Permissions."
                            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                                "Health Connect needs an update. Update it from the Play Store, then tap Grant Permissions."
                            else ->
                                "Tap Grant Permissions for the system dialog. If it was previously denied and won't show, tap Open Health Connect App and toggle permissions on manually."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
            }
        }

        // --- Background Sync + Diagnostics ---
        item {
            SyncSection(state = state, viewModel = viewModel)
        }

        // --- Data History ---
        item {
            DataHistorySection(state = state, viewModel = viewModel)
        }

        // --- Garmin Connect ---
        item {
            SettingsSection("Garmin Connect API (Optional)") {
                Text(
                    "Register a developer app at developer.garmin.com to get your Consumer Key & Secret for full Garmin data access (GPS routes, laps, VO2 max). Not required — Health Connect handles most data.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                Spacer(Modifier.height(12.dp))
                StatusRow(
                    label = "Status",
                    value = if (state.garminConnected) "Connected" else "Not connected",
                    valueColor = if (state.garminConnected) TealPrimary else CoralAccent,
                )
                if (!state.garminConnected) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = garminKey,
                        onValueChange = { garminKey = it },
                        label = { Text("Consumer Key", color = TextSecondary) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = textFieldColors(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = garminSecret,
                        onValueChange = { garminSecret = it },
                        label = { Text("Consumer Secret", color = TextSecondary) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = textFieldColors(),
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            viewModel.saveGarminCredentials(garminKey, garminSecret)
                            viewModel.connectGarmin()
                        },
                        enabled = garminKey.isNotBlank() && garminSecret.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Connect Garmin", color = MaterialTheme.colorScheme.background)
                    }
                } else {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { viewModel.disconnectGarmin() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CoralAccent),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CoralAccent),
                    ) {
                        Text("Disconnect Garmin")
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncSection(state: SettingsUiState, viewModel: SettingsViewModel) {
    SettingsSection("Background Sync & Diagnostics") {
        SettingsRow(
            label = "Background Sync",
            description = "Check Health Connect every 15 minutes",
        ) {
            Switch(
                checked = state.backgroundSyncEnabled,
                onCheckedChange = { viewModel.setBackgroundSyncEnabled(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = TealPrimary,
                    checkedTrackColor = TealPrimary.copy(alpha = 0.4f),
                ),
            )
        }
        Spacer(Modifier.height(12.dp))
        StatusRow(
            label = "Change Token",
            value = if (state.hcChangesTokenPresent) "Present (incremental sync active)" else "None (full fetch on next sync)",
            valueColor = if (state.hcChangesTokenPresent) TealPrimary else TextSecondary,
        )
        Spacer(Modifier.height(4.dp))
        StatusRow(
            label = "Cached Activities",
            value = "${state.cacheActivityCount}",
            valueColor = TextSecondary,
        )
        Spacer(Modifier.height(4.dp))
        StatusRow(
            label = "Last Manual Sync",
            value = state.lastSyncLabel,
            valueColor = TextSecondary,
        )
        if (state.backgroundSyncEnabled) {
            Spacer(Modifier.height(4.dp))
            StatusRow(
                label = "Last Background Sync",
                value = state.lastBackgroundSyncLabel,
                valueColor = TextSecondary,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DataHistorySection(state: SettingsUiState, viewModel: SettingsViewModel) {
    val ranges = listOf(
        1 to "1 Day",
        7 to "1 Week",
        30 to "1 Month",
        90 to "3 Months",
        180 to "6 Months",
        365 to "1 Year",
    )
    SettingsSection("Data History") {
        Text(
            "How far back to load data from Health Connect",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )
        Spacer(Modifier.height(12.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ranges.forEach { (days, label) ->
                FilterChip(
                    selected = state.dataDaysBack == days,
                    onClick = { viewModel.setDataDaysBack(days) },
                    label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = TealPrimary.copy(alpha = 0.2f),
                        selectedLabelColor = TealPrimary,
                        labelColor = TextSecondary,
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = state.dataDaysBack == days,
                        selectedBorderColor = TealPrimary,
                        borderColor = BorderColor,
                    ),
                )
            }
        }
    }
}

private fun openHealthConnectPermissionsPage(context: android.content.Context) {
    val packageName = context.packageName
    val intents = listOf(
        // Directly open RunConnect's permissions page in Health Connect
        Intent("android.health.connect.action.MANAGE_HEALTH_PERMISSIONS")
            .putExtra(Intent.EXTRA_PACKAGE_NAME, packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        // Open HC app main page
        context.packageManager.getLaunchIntentForPackage("com.google.android.apps.healthdata")
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        // Play Store as last resort
        Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.healthdata"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
    for (intent in intents) {
        if (intent == null) continue
        try {
            context.startActivity(intent)
            return
        } catch (_: Exception) { }
    }
    Toast.makeText(context, "Could not open Health Connect. Install it from the Play Store.", Toast.LENGTH_LONG).show()
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .padding(bottom = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardDark)
            .padding(16.dp)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = TealPrimary,
        )
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun SettingsRow(
    label: String,
    description: String? = null,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary))
            description?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
        Spacer(Modifier.width(8.dp))
        trailing()
    }
}

@Composable
private fun StatusRow(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = valueColor)
    }
}

@Composable
private fun textFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = TealPrimary,
    unfocusedBorderColor = BorderColor,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    cursorColor = TealPrimary,
)
