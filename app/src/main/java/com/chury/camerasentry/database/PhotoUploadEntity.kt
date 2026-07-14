package com.chury.camerasentry.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "photo_uploads")
data class PhotoUploadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val filePath: String,
    val fileName: String,
    val capturedAtMillis: Long,
    val differencePercent: Double?,
    val status: UploadStatus = UploadStatus.PENDING,
    val attempts: Int = 0,
    val lastError: String? = null
)

enum class UploadStatus {
    PENDING,
    UPLOADING,
    UPLOADED,
    FAILED
}
