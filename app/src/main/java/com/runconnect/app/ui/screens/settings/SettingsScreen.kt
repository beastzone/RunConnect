package com.runconnect.app.ui.screens.settings

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.health.connect.client.HealthConnectClient
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
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

    val permissionLauncher = rememberLauncherForActivityResult(
        HealthConnectClient.requestPermissionActivityContract()
    ) {
        viewModel.refreshPermissions()
    }

    var garminKey by remember { mutableStateOf("") }
    var garminSecret by remember { mutableStateOf("") }
    var mapboxToken by remember { mutableStateOf(state.mapboxToken) }
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
                    label = "Available",
                    value = if (state.healthConnectAvailable) "Yes" else "Not installed",
                    valueColor = if (state.healthConnectAvailable) TealPrimary else CoralAccent,
                )
                StatusRow(
                    label = "Permissions",
                    value = if (state.healthConnectPermissionsGranted) "Granted" else "Not granted",
                    valueColor = if (state.healthConnectPermissionsGranted) TealPrimary else CoralAccent,
                )
                if (!state.healthConnectPermissionsGranted && state.healthConnectAvailable) {
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { permissionLauncher.launch(viewModel.requiredPermissions) },
                        colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Grant Permissions", color = MaterialTheme.colorScheme.background)
                    }
                } else if (!state.healthConnectAvailable) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Health Connect is not installed. Install it from the Play Store to use this app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
            }
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

        // --- Mapbox ---
        item {
            SettingsSection("Mapbox (for 3D maps)") {
                Text(
                    "Get a free public token at account.mapbox.com. Required for 3D route maps.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = mapboxToken,
                    onValueChange = { mapboxToken = it },
                    label = { Text("Mapbox Public Token", color = TextSecondary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = textFieldColors(),
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.setMapboxToken(mapboxToken) },
                    enabled = mapboxToken.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Save Token", color = MaterialTheme.colorScheme.background)
                }
            }
        }
    }
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
