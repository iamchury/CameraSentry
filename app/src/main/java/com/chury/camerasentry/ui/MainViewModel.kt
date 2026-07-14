package com.chury.camerasentry.ui

import android.app.Application
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chury.camerasentry.database.AppStateEntity
import com.chury.camerasentry.database.CameraSentryDatabase
import com.chury.camerasentry.repository.MonitoringRepository
import com.chury.camerasentry.repository.UploadScheduler
import com.chury.camerasentry.service.MonitoringService
import com.chury.camerasentry.settings.DriveConnectionState
import com.chury.camerasentry.settings.MonitoringSettings
import com.chury.camerasentry.settings.SettingsDataStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsDataStore = SettingsDataStore(application)
    private val repository = MonitoringRepository(
        CameraSentryDatabase.get(application).dao(),
        UploadScheduler(application)
    )

    val uiState: StateFlow<MainUiState> = combine(
        settingsDataStore.monitoringSettings,
        settingsDataStore.driveState,
        repository.appState,
        repository.todayPreservedCount(),
        repository.uploadQueueCount
    ) { settings, drive, appState, preservedCount, queueCount ->
        MainUiState(
            settings = settings,
            drive = drive,
            appState = appState,
            todayPreservedCount = preservedCount,
            uploadQueueCount = queueCount
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        MainUiState()
    )

    fun saveSettings(settings: MonitoringSettings) {
        viewModelScope.launch {
            settingsDataStore.saveMonitoringSettings(settings)
        }
    }

    fun startMonitoring() {
        val context = getApplication<Application>()
        ContextCompat.startForegroundService(context, MonitoringService.startIntent(context))
    }

    fun stopMonitoring() {
        val context = getApplication<Application>()
        context.startService(MonitoringService.stopIntent(context))
    }

    fun saveDriveConnection(accessToken: String) {
        viewModelScope.launch {
            settingsDataStore.saveDriveToken(accessToken, null)
        }
    }

    fun disconnectDrive() {
        viewModelScope.launch {
            settingsDataStore.clearDriveToken()
        }
    }

    fun reportError(message: String) {
        viewModelScope.launch {
            repository.updateState { it.copy(recentError = message) }
        }
    }
}

data class MainUiState(
    val settings: MonitoringSettings = MonitoringSettings(),
    val drive: DriveConnectionState = DriveConnectionState(),
    val appState: AppStateEntity = AppStateEntity(),
    val todayPreservedCount: Int = 0,
    val uploadQueueCount: Int = 0
) {
    val monitoringActive: Boolean = appState.monitoringActive
}
