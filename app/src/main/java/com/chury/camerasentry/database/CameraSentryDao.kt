package com.chury.camerasentry.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CameraSentryDao {
    @Query("SELECT * FROM app_state WHERE id = 1")
    fun observeAppState(): Flow<AppStateEntity?>

    @Query("SELECT * FROM app_state WHERE id = 1")
    suspend fun getAppState(): AppStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAppState(state: AppStateEntity)

    @Insert
    suspend fun insertUpload(upload: PhotoUploadEntity): Long

    @Update
    suspend fun updateUpload(upload: PhotoUploadEntity)

    @Query("SELECT * FROM photo_uploads WHERE status IN ('PENDING', 'FAILED') ORDER BY capturedAtMillis ASC LIMIT 1")
    suspend fun nextUpload(): PhotoUploadEntity?

    @Query("SELECT COUNT(*) FROM photo_uploads WHERE status IN ('PENDING', 'FAILED', 'UPLOADING')")
    fun observeUploadQueueCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM photo_uploads WHERE filePath = :filePath")
    suspend fun uploadCountForPath(filePath: String): Int

    @Query("SELECT COUNT(*) FROM photo_uploads WHERE capturedAtMillis >= :startMillis")
    fun observePreservedCountSince(startMillis: Long): Flow<Int>
}
