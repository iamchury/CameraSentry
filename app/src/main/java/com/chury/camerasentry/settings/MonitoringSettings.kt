package com.chury.camerasentry.settings

data class MonitoringSettings(
    val captureIntervalSeconds: Long = DEFAULT_CAPTURE_INTERVAL_SECONDS,
    val comparisonEnabled: Boolean = true,
    val differenceThresholdPercent: Int = DEFAULT_DIFFERENCE_THRESHOLD_PERCENT,
    val storageTarget: StorageTarget = StorageTarget.GOOGLE_DRIVE
) {
    fun validated(): MonitoringSettings = copy(
        captureIntervalSeconds = captureIntervalSeconds.coerceIn(
            MIN_CAPTURE_INTERVAL_SECONDS,
            MAX_CAPTURE_INTERVAL_SECONDS
        ),
        differenceThresholdPercent = differenceThresholdPercent.coerceIn(
            MIN_DIFFERENCE_THRESHOLD_PERCENT,
            MAX_DIFFERENCE_THRESHOLD_PERCENT
        )
    )

    companion object {
        const val MIN_CAPTURE_INTERVAL_SECONDS = 10L
        const val MAX_CAPTURE_INTERVAL_SECONDS = 86_400L
        const val DEFAULT_CAPTURE_INTERVAL_SECONDS = 60L
        const val MIN_DIFFERENCE_THRESHOLD_PERCENT = 1
        const val MAX_DIFFERENCE_THRESHOLD_PERCENT = 99
        const val DEFAULT_DIFFERENCE_THRESHOLD_PERCENT = 10
    }
}

enum class StorageTarget {
    GOOGLE_DRIVE,
    LOCAL
}
