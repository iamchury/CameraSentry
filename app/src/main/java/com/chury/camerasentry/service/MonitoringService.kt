package com.chury.camerasentry.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.pm.PackageManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.chury.camerasentry.DecisionText
import com.chury.camerasentry.MainActivity
import com.chury.camerasentry.R
import com.chury.camerasentry.camera.CameraCaptureManager
import com.chury.camerasentry.comparison.ImageDifferenceCalculator
import com.chury.camerasentry.database.CameraSentryDatabase
import com.chury.camerasentry.repository.MonitoringRepository
import com.chury.camerasentry.repository.UploadScheduler
import com.chury.camerasentry.settings.SettingsDataStore
import com.chury.camerasentry.settings.StorageTarget
import com.chury.camerasentry.storage.PhotoStorage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MonitoringService : LifecycleService() {
    private lateinit var repository: MonitoringRepository
    private lateinit var settingsDataStore: SettingsDataStore
    private lateinit var storage: PhotoStorage
    private lateinit var camera: CameraCaptureManager
    private lateinit var differenceCalculator: ImageDifferenceCalculator
    private var monitoringJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        val dao = CameraSentryDatabase.get(this).dao()
        repository = MonitoringRepository(dao, UploadScheduler(applicationContext))
        settingsDataStore = SettingsDataStore(applicationContext)
        storage = PhotoStorage(applicationContext)
        camera = CameraCaptureManager(applicationContext, this)
        differenceCalculator = ImageDifferenceCalculator(applicationContext)
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> startMonitoring()
            ACTION_STOP -> stopMonitoring(intentional = true)
            null -> stopSelf(startId)
            else -> stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        monitoringJob?.cancel()
        monitoringJob = null
        camera.stop()
        super.onDestroy()
    }

    private fun startMonitoring() {
        startForeground(NOTIFICATION_ID, notification())
        if (monitoringJob?.isActive == true) return
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            lifecycleScope.launch {
                repository.updateState {
                    it.copy(
                        monitoringActive = false,
                        recentError = getString(R.string.error_camera_permission_missing)
                    )
                }
                stopSelf()
            }
            return
        }
        monitoringJob = lifecycleScope.launch {
            val settings = settingsDataStore.monitoringSettings.first().validated()
            repository.updateState {
                it.copy(
                    monitoringActive = true,
                    recentDecision = DecisionText.monitoringStarted(),
                    recentError = null
                )
            }

            while (isActive) {
                val capturedAt = System.currentTimeMillis()
                val output = storage.createPhotoFile(capturedAt)
                runCatching {
                    camera.capture(output)
                    processCapturedPhoto(output.absolutePath, capturedAt)
                }.onFailure { error ->
                    storage.delete(output.absolutePath)
                    repository.updateState { it.copy(recentError = error.message) }
                }
                val nextAt = System.currentTimeMillis() + settings.captureIntervalSeconds * 1000L
                repository.updateState { it.copy(nextCaptureTimeMillis = nextAt) }
                delay(settings.captureIntervalSeconds * 1000L)
            }
        }
    }

    private suspend fun processCapturedPhoto(currentPath: String, capturedAt: Long) {
        val settings = settingsDataStore.monitoringSettings.first().validated()
        val state = repository.getState()
        var recentDifference: Double? = null
        var decision = DecisionText.firstPhotoPending()
        var pendingPath: String? = currentPath

        if (!settings.comparisonEnabled) {
            preservePhoto(currentPath, capturedAt, null, settings.storageTarget)
            pendingPath = null
            decision = DecisionText.comparisonOffPreserve()
        } else {
            val previousPath = state.pendingPhotoPath
            if (previousPath != null) {
                val difference = differenceCalculator.differencePercent(previousPath, currentPath)
                recentDifference = difference
                if (difference >= settings.differenceThresholdPercent) {
                    preservePhoto(
                        previousPath,
                        state.lastCaptureTimeMillis ?: capturedAt,
                        difference,
                        settings.storageTarget
                    )
                    decision = DecisionText.differencePreserve(difference)
                } else {
                    storage.delete(previousPath)
                    decision = DecisionText.differenceDelete(difference)
                }
            }
        }

        repository.updateState {
            val today = DateTimeFormatter.ISO_LOCAL_DATE.format(
                Instant.ofEpochMilli(capturedAt).atZone(ZoneId.systemDefault()).toLocalDate()
            )
            val count = if (it.todayCaptureDate == today) it.todayCaptureCount + 1 else 1
            it.copy(
                monitoringActive = true,
                lastCaptureTimeMillis = capturedAt,
                pendingPhotoPath = pendingPath,
                recentDifferencePercent = recentDifference,
                recentDecision = decision,
                recentError = null,
                todayCaptureDate = today,
                todayCaptureCount = count
            )
        }
    }

    private suspend fun preservePhoto(
        path: String,
        capturedAt: Long,
        differencePercent: Double?,
        storageTarget: StorageTarget
    ) {
        when (storageTarget) {
            StorageTarget.GOOGLE_DRIVE -> repository.enqueuePhoto(path, capturedAt, differencePercent)
            StorageTarget.LOCAL -> {
                val localPhoto = storage.preserveLocal(path, capturedAt)
                repository.recordLocalPhoto(localPhoto.uri, localPhoto.fileName, capturedAt, differencePercent)
            }
        }
    }

    private fun stopMonitoring(intentional: Boolean) {
        val job = monitoringJob
        monitoringJob = null
        job?.cancel()
        lifecycleScope.launch {
            val state = repository.getState()
            if (intentional && state.pendingPhotoPath != null) {
                val settings = settingsDataStore.monitoringSettings.first().validated()
                runCatching {
                    preservePhoto(
                        state.pendingPhotoPath,
                        state.lastCaptureTimeMillis ?: System.currentTimeMillis(),
                        state.recentDifferencePercent,
                        settings.storageTarget
                    )
                }.onFailure { error ->
                    repository.updateState { it.copy(recentError = error.message) }
                }
            }
            repository.updateState {
                it.copy(
                    monitoringActive = false,
                    nextCaptureTimeMillis = null,
                    pendingPhotoPath = null,
                    recentDecision = if (intentional) {
                        DecisionText.monitoringStoppedPreserve()
                    } else {
                        it.recentDecision
                    }
                )
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun notification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_monitoring_text))
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_monitoring),
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "monitoring"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_START = "com.chury.camerasentry.START"
        private const val ACTION_STOP = "com.chury.camerasentry.STOP"

        fun startIntent(context: Context): Intent =
            Intent(context, MonitoringService::class.java).setAction(ACTION_START)

        fun stopIntent(context: Context): Intent =
            Intent(context, MonitoringService::class.java).setAction(ACTION_STOP)
    }
}
