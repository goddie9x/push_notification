package com.god.notifypush

import kotlinx.serialization.Serializable

@Serializable
data class AppSettings(
    val otpFilterEnabled: Boolean = true,
    val otpCustomPatterns: List<String> = emptyList(),
    val historyEnabled: Boolean = true,
    val historyRetentionDays: Int = 30,
    val autoRetryEnabled: Boolean = true,
    val retryIntervalMinutes: Int = 15,
) {
    fun normalized(): AppSettings = copy(
        historyRetentionDays = historyRetentionDays.takeIf { it in HISTORY_RETENTION_OPTIONS }
            ?: DEFAULT_HISTORY_RETENTION_DAYS,
        retryIntervalMinutes = retryIntervalMinutes.takeIf { it in RETRY_INTERVAL_OPTIONS }
            ?: DEFAULT_RETRY_INTERVAL_MINUTES,
    )

    companion object {
        const val DEFAULT_HISTORY_RETENTION_DAYS = 30
        const val DEFAULT_RETRY_INTERVAL_MINUTES = 15
        val HISTORY_RETENTION_OPTIONS = listOf(7, 30, 90)
        val RETRY_INTERVAL_OPTIONS = listOf(5, 15, 60)
    }
}
