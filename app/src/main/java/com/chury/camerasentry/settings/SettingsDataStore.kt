package com.chury.camerasentry.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "camera_sentry_settings")

class SettingsDataStore(private val context: Context) {
    val monitoringSettings: Flow<MonitoringSettings> = context.dataStore.data.map { prefs ->
        MonitoringSettings(
            captureIntervalSeconds = prefs[CAPTURE_INTERVAL_SECONDS]
                ?: MonitoringSettings.DEFAULT_CAPTURE_INTERVAL_SECONDS,
            comparisonEnabled = prefs[COMPARISON_ENABLED] ?: true,
            differenceThresholdPercent = prefs[DIFFERENCE_THRESHOLD_PERCENT]
                ?: MonitoringSettings.DEFAULT_DIFFERENCE_THRESHOLD_PERCENT,
            storageTarget = prefs[STORAGE_TARGET]?.let { value ->
                runCatching { StorageTarget.valueOf(value) }.getOrNull()
            } ?: StorageTarget.GOOGLE_DRIVE
        ).validated()
    }

    val driveState: Flow<DriveConnectionState> = context.dataStore.data.map { prefs ->
        DriveConnectionState(
            accessToken = prefs[DRIVE_ACCESS_TOKEN],
            accountEmail = prefs[DRIVE_ACCOUNT_EMAIL]
        )
    }

    suspend fun saveMonitoringSettings(settings: MonitoringSettings) {
        val validated = settings.validated()
        context.dataStore.edit { prefs ->
            prefs[CAPTURE_INTERVAL_SECONDS] = validated.captureIntervalSeconds
            prefs[COMPARISON_ENABLED] = validated.comparisonEnabled
            prefs[DIFFERENCE_THRESHOLD_PERCENT] = validated.differenceThresholdPercent
            prefs[STORAGE_TARGET] = validated.storageTarget.name
        }
    }

    suspend fun saveDriveToken(accessToken: String, accountEmail: String?) {
        context.dataStore.edit { prefs ->
            prefs[DRIVE_ACCESS_TOKEN] = accessToken
            if (accountEmail == null) {
                prefs.remove(DRIVE_ACCOUNT_EMAIL)
            } else {
                prefs[DRIVE_ACCOUNT_EMAIL] = accountEmail
            }
        }
    }

    suspend fun clearDriveToken() {
        context.dataStore.edit { prefs ->
            prefs.remove(DRIVE_ACCESS_TOKEN)
            prefs.remove(DRIVE_ACCOUNT_EMAIL)
        }
    }

    private companion object {
        val CAPTURE_INTERVAL_SECONDS = longPreferencesKey("capture_interval_seconds")
        val COMPARISON_ENABLED = booleanPreferencesKey("comparison_enabled")
        val DIFFERENCE_THRESHOLD_PERCENT = intPreferencesKey("difference_threshold_percent")
        val STORAGE_TARGET = stringPreferencesKey("storage_target")
        val DRIVE_ACCESS_TOKEN = stringPreferencesKey("drive_access_token")
        val DRIVE_ACCOUNT_EMAIL = stringPreferencesKey("drive_account_email")
    }
}

data class DriveConnectionState(
    val accessToken: String? = null,
    val accountEmail: String? = null
) {
    val connected: Boolean = !accessToken.isNullOrBlank()
}
