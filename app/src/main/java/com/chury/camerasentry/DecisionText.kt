package com.chury.camerasentry

import android.content.Context

object DecisionText {
    private const val PREFIX = "decision:"
    private const val MONITORING_STARTED = "monitoring_started"
    private const val FIRST_PHOTO_PENDING = "first_photo_pending"
    private const val COMPARISON_OFF_PRESERVE = "comparison_off_preserve"
    private const val DIFFERENCE_PRESERVE = "difference_preserve"
    private const val DIFFERENCE_DELETE = "difference_delete"
    private const val MONITORING_STOPPED_PRESERVE = "monitoring_stopped_preserve"

    private val legacyDifferenceDelete = Regex(
        """^(?:Difference|차이)\s+([0-9]+(?:\.[0-9]+)?)%:\s*(?:previous photo deleted|이전 사진 삭제)$"""
    )
    private val legacyDifferencePreserve = Regex(
        """^(?:Difference|차이)\s+([0-9]+(?:\.[0-9]+)?)%:\s*(?:previous photo preserved|이전 사진 보존)$"""
    )

    fun monitoringStarted(): String = token(MONITORING_STARTED)

    fun firstPhotoPending(): String = token(FIRST_PHOTO_PENDING)

    fun comparisonOffPreserve(): String = token(COMPARISON_OFF_PRESERVE)

    fun differencePreserve(difference: Double): String = token(DIFFERENCE_PRESERVE, difference)

    fun differenceDelete(difference: Double): String = token(DIFFERENCE_DELETE, difference)

    fun monitoringStoppedPreserve(): String = token(MONITORING_STOPPED_PRESERVE)

    fun resolve(context: Context, value: String?): String {
        if (value.isNullOrBlank()) return context.getString(R.string.none)
        return parseToken(value)?.localized(context)
            ?: parseLegacy(value)?.localized(context)
            ?: value
    }

    private fun token(key: String, difference: Double? = null): String =
        buildString {
            append(PREFIX)
            append(key)
            if (difference != null) {
                append(':')
                append(difference)
            }
        }

    private fun parseToken(value: String): Decision? {
        if (!value.startsWith(PREFIX)) return null
        val parts = value.removePrefix(PREFIX).split(':')
        val key = parts.getOrNull(0) ?: return null
        val difference = parts.getOrNull(1)?.toDoubleOrNull()
        return Decision(key, difference)
    }

    private fun parseLegacy(value: String): Decision? {
        val normalized = value.trim()
        legacyDifferenceDelete.matchEntire(normalized)?.let {
            return Decision(DIFFERENCE_DELETE, it.groupValues[1].toDoubleOrNull())
        }
        legacyDifferencePreserve.matchEntire(normalized)?.let {
            return Decision(DIFFERENCE_PRESERVE, it.groupValues[1].toDoubleOrNull())
        }
        return when (normalized) {
            "Monitoring started", "감시 시작" -> Decision(MONITORING_STARTED)
            "First photo kept pending", "첫 사진을 대기 상태로 보관" -> Decision(FIRST_PHOTO_PENDING)
            "Comparison off: photo preserved", "비교 꺼짐: 사진 보존" -> Decision(COMPARISON_OFF_PRESERVE)
            "Monitoring stopped: last photo preserved", "감시 중지: 마지막 사진 보존" -> {
                Decision(MONITORING_STOPPED_PRESERVE)
            }
            else -> null
        }
    }

    private data class Decision(
        val key: String,
        val difference: Double? = null
    ) {
        fun localized(context: Context): String? =
            when (key) {
                MONITORING_STARTED -> context.getString(R.string.decision_monitoring_started)
                FIRST_PHOTO_PENDING -> context.getString(R.string.decision_first_photo_pending)
                COMPARISON_OFF_PRESERVE -> context.getString(R.string.decision_comparison_off_preserve)
                DIFFERENCE_PRESERVE -> difference?.let {
                    context.getString(R.string.decision_difference_preserve, it)
                }
                DIFFERENCE_DELETE -> difference?.let {
                    context.getString(R.string.decision_difference_delete, it)
                }
                MONITORING_STOPPED_PRESERVE -> context.getString(R.string.decision_monitoring_stopped_preserve)
                else -> null
            }
    }
}
