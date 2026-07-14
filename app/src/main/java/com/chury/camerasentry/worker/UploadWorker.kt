package com.chury.camerasentry.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.chury.camerasentry.database.CameraSentryDatabase
import com.chury.camerasentry.database.UploadStatus
import com.chury.camerasentry.drive.DriveUploader
import java.io.File

class UploadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val dao = CameraSentryDatabase.get(applicationContext).dao()
        val upload = dao.nextUpload() ?: return Result.success()
        dao.updateUpload(upload.copy(status = UploadStatus.UPLOADING))
        val result = DriveUploader(applicationContext).upload(
            file = File(upload.filePath),
            capturedAtMillis = upload.capturedAtMillis
        )
        return result.fold(
            onSuccess = {
                dao.updateUpload(upload.copy(status = UploadStatus.UPLOADED, lastError = null))
                enqueue(applicationContext)
                Result.success()
            },
            onFailure = { throwable ->
                dao.updateUpload(
                    upload.copy(
                        status = UploadStatus.FAILED,
                        attempts = upload.attempts + 1,
                        lastError = throwable.message
                    )
                )
                Result.retry()
            }
        )
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "drive_upload_queue"

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<UploadWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request
            )
        }
    }
}
