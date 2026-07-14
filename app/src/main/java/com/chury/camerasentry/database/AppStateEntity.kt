package com.chury.camerasentry.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_state")
data class AppStateEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val monitoringActive: Boolean = false,
    val lastCaptureTimeMillis: Long? = null,
    val nextCaptureTimeMillis: Long? = null,
    val pendingPhotoPath: String? = null,
    val recentDifferencePercent: Double? = null,
    val recentDecision: String? = null,
    val recentError: String? = null,
    val todayCaptureDate: String? = null,
    val todayCaptureCount: Int = 0
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}
