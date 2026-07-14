package com.chury.camerasentry.repository

import com.chury.camerasentry.database.AppStateEntity
import com.chury.camerasentry.database.CameraSentryDao
import com.chury.camerasentry.database.PhotoUploadEntity
import com.chury.camerasentry.database.UploadStatus
import com.chury.camerasentry.worker.UploadWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

class MonitoringRepository(
    private val dao: CameraSentryDao,
    private val uploadScheduler: UploadScheduler
) {
    val appState: Flow<AppStateEntity> = dao.observeAppState().map {
        it ?: AppStateEntity()
    }

    val uploadQueueCount: Flow<Int> = dao.observeUploadQueueCount()

    fun todayPreservedCount(): Flow<Int> {
        val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return dao.observePreservedCountSince(start)
    }

    suspend fun getState(): AppStateEntity = dao.getAppState() ?: AppStateEntity()

    suspend fun updateState(transform: (AppStateEntity) -> AppStateEntity) {
        dao.upsertAppState(transform(getState()))
    }

    suspend fun enqueuePhoto(filePath: String, capturedAtMillis: Long, differencePercent: Double?) {
        if (dao.uploadCountForPath(filePath) > 0) return
        dao.insertUpload(
            PhotoUploadEntity(
                filePath = filePath,
                fileName = File(filePath).name,
                capturedAtMillis = capturedAtMillis,
                differencePercent = differencePercent,
                status = UploadStatus.PENDING
            )
        )
        uploadScheduler.schedule()
    }

    suspend fun recordLocalPhoto(
        filePath: String,
        fileName: String,
        capturedAtMillis: Long,
        differencePercent: Double?
    ) {
        if (dao.uploadCountForPath(filePath) > 0) return
        dao.insertUpload(
            PhotoUploadEntity(
                filePath = filePath,
                fileName = fileName,
                capturedAtMillis = capturedAtMillis,
                differencePercent = differencePercent,
                status = UploadStatus.UPLOADED
            )
        )
    }

    suspend fun nextUpload(): PhotoUploadEntity? = dao.nextUpload()

    suspend fun updateUpload(upload: PhotoUploadEntity) {
        dao.updateUpload(upload)
        if (upload.status == UploadStatus.PENDING || upload.status == UploadStatus.FAILED) {
            UploadWorker.enqueue(uploadScheduler.context)
        }
    }
}

class UploadScheduler(val context: android.content.Context) {
    fun schedule() = UploadWorker.enqueue(context)
}
