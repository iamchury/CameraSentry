package com.chury.camerasentry

import android.Manifest
import android.app.Activity
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.chury.camerasentry.drive.DriveAuthorizationManager
import com.chury.camerasentry.settings.MonitoringSettings
import com.chury.camerasentry.settings.StorageTarget
import com.chury.camerasentry.ui.MainUiState
import com.chury.camerasentry.ui.MainViewModel
import com.chury.camerasentry.ui.theme.CameraSentryTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private lateinit var driveAuthorizationManager: DriveAuthorizationManager
    private var viewModelRef: MainViewModel? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.CAMERA] == true
        if (granted) {
            viewModelRef?.startMonitoring()
        } else {
            viewModelRef?.reportError(getString(R.string.camera_permission_required))
        }
    }

    private val driveLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            driveAuthorizationManager.resultFromIntent(result.data)
                .onSuccess(::handleDriveAuthorization)
                .onFailure { error ->
                    viewModelRef?.reportError(error.message ?: getString(R.string.drive_connection_failed))
                }
        } else {
            viewModelRef?.reportError(getString(R.string.drive_connection_cancelled, result.resultCode))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        driveAuthorizationManager = DriveAuthorizationManager(this)
        enableEdgeToEdge()
        setContent {
            val viewModel: MainViewModel = viewModel()
            viewModelRef = viewModel
            val uiState by viewModel.uiState.collectAsState()
            CameraSentryTheme {
                CameraSentryScreen(
                    uiState = uiState,
                    onSettingsChange = viewModel::saveSettings,
                    onStart = {
                        if (uiState.settings.storageTarget == StorageTarget.GOOGLE_DRIVE && !uiState.drive.connected) {
                            viewModel.reportError(getString(R.string.connect_drive_before_start))
                        } else {
                            permissionLauncher.launch(requiredPermissions())
                        }
                    },
                    onStop = viewModel::stopMonitoring,
                    onConnectDrive = { connectDrive() },
                    onDisconnectDrive = viewModel::disconnectDrive
                )
            }
        }
    }

    private fun requiredPermissions(): Array<String> =
        buildList {
            add(Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()

    private fun connectDrive() {
        driveAuthorizationManager.requestAuthorization(
            onResolutionRequired = { request -> driveLauncher.launch(request) },
            onAuthorized = ::handleDriveAuthorization,
            onError = { message -> viewModelRef?.reportError(message) }
        )
    }

    private fun handleDriveAuthorization(result: AuthorizationResult) {
        val token = result.accessToken
        if (token.isNullOrBlank()) {
            viewModelRef?.reportError(getString(R.string.drive_access_token_missing))
        } else {
            viewModelRef?.saveDriveConnection(token)
        }
    }
}

@Composable
private fun CameraSentryScreen(
    uiState: MainUiState,
    onSettingsChange: (MonitoringSettings) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onConnectDrive: () -> Unit,
    onDisconnectDrive: () -> Unit
) {
    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            StatusCard(uiState)
            SettingsCard(uiState, onSettingsChange)
            ActionCard(uiState, onStart, onStop, onConnectDrive, onDisconnectDrive)
            MetricsCard(uiState)
        }
    }
}

@Composable
private fun StatusCard(uiState: MainUiState) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoRow(
                stringResource(R.string.status_current_operation),
                if (uiState.monitoringActive) {
                    stringResource(R.string.status_monitoring)
                } else {
                    stringResource(R.string.status_stopped)
                }
            )
            InfoRow(
                stringResource(R.string.status_drive_connection),
                if (uiState.drive.connected) {
                    stringResource(R.string.status_connected)
                } else {
                    stringResource(R.string.status_not_connected)
                }
            )
            InfoRow(stringResource(R.string.status_recent_error), uiState.appState.recentError ?: stringResource(R.string.none))
        }
    }
}

@Composable
private fun SettingsCard(
    uiState: MainUiState,
    onSettingsChange: (MonitoringSettings) -> Unit
) {
    var interval by remember { mutableStateOf(uiState.settings.captureIntervalSeconds.toString()) }
    var threshold by remember { mutableStateOf(uiState.settings.differenceThresholdPercent.toString()) }

    LaunchedEffect(uiState.settings) {
        interval = uiState.settings.captureIntervalSeconds.toString()
        threshold = uiState.settings.differenceThresholdPercent.toString()
    }

    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.settings_storage_target), style = MaterialTheme.typography.bodyMedium)
            StorageTarget.entries.forEach { target ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = uiState.settings.storageTarget == target,
                        enabled = !uiState.monitoringActive,
                        onClick = {
                            onSettingsChange(uiState.settings.copy(storageTarget = target))
                        }
                    )
                    Text(
                        text = when (target) {
                            StorageTarget.GOOGLE_DRIVE -> stringResource(R.string.storage_target_google_drive)
                            StorageTarget.LOCAL -> stringResource(R.string.storage_target_local)
                        }
                    )
                }
            }
            if (uiState.settings.storageTarget == StorageTarget.LOCAL) {
                Text(
                    text = stringResource(R.string.local_storage_path_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            OutlinedTextField(
                value = interval,
                onValueChange = { value ->
                    interval = value.filter(Char::isDigit)
                    interval.toLongOrNull()?.let {
                        onSettingsChange(uiState.settings.copy(captureIntervalSeconds = it).validated())
                    }
                },
                enabled = !uiState.monitoringActive,
                label = { Text(stringResource(R.string.settings_capture_interval)) },
                suffix = { Text(stringResource(R.string.seconds_suffix)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.settings_delete_duplicates))
                Switch(
                    checked = uiState.settings.comparisonEnabled,
                    enabled = !uiState.monitoringActive,
                    onCheckedChange = {
                        onSettingsChange(uiState.settings.copy(comparisonEnabled = it))
                    }
                )
            }
            OutlinedTextField(
                value = threshold,
                onValueChange = { value ->
                    threshold = value.filter(Char::isDigit)
                    threshold.toIntOrNull()?.let {
                        onSettingsChange(uiState.settings.copy(differenceThresholdPercent = it).validated())
                    }
                },
                enabled = !uiState.monitoringActive && uiState.settings.comparisonEnabled,
                label = { Text(stringResource(R.string.settings_difference_threshold)) },
                suffix = { Text("%") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            if (uiState.monitoringActive) {
                Text(
                    text = stringResource(R.string.settings_stop_to_edit),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

@Composable
private fun ActionCard(
    uiState: MainUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onConnectDrive: () -> Unit,
    onDisconnectDrive: () -> Unit
) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onStart, enabled = !uiState.monitoringActive, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.action_start))
                }
                Button(onClick = onStop, enabled = uiState.monitoringActive, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.action_stop))
                }
            }
            TextButton(
                onClick = if (uiState.drive.connected) onDisconnectDrive else onConnectDrive,
                enabled = !uiState.monitoringActive,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (uiState.drive.connected) {
                        stringResource(R.string.action_disconnect_drive)
                    } else {
                        stringResource(R.string.action_connect_drive)
                    }
                )
            }
        }
    }
}

@Composable
private fun MetricsCard(uiState: MainUiState) {
    val context = LocalContext.current
    val none = stringResource(R.string.none)
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.metrics_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            InfoRow(stringResource(R.string.metrics_last_capture_time), formatTime(context, uiState.appState.lastCaptureTimeMillis))
            InfoRow(stringResource(R.string.metrics_next_capture_time), formatTime(context, uiState.appState.nextCaptureTimeMillis))
            InfoRow(stringResource(R.string.metrics_today_capture_count), "${uiState.appState.todayCaptureCount}")
            InfoRow(stringResource(R.string.metrics_today_preserved_count), "${uiState.todayPreservedCount}")
            InfoRow(stringResource(R.string.metrics_upload_queue_count), "${uiState.uploadQueueCount}")
            InfoRow(
                stringResource(R.string.metrics_recent_difference),
                uiState.appState.recentDifferencePercent?.let { "%.2f%%".format(it) } ?: none
            )
            InfoRow(
                stringResource(R.string.metrics_recent_decision),
                DecisionText.resolve(context, uiState.appState.recentDecision)
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
        HorizontalDivider()
    }
}

private fun formatTime(context: android.content.Context, millis: Long?): String {
    if (millis == null) return context.getString(R.string.none)
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(millis))
}
