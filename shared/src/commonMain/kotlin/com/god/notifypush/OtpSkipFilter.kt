package com.god.notifypush

object OtpSkipFilter {
    var enabled: Boolean = true
    var customPatterns: List<Regex> = emptyList()

    private val defaultPatterns: List<Regex> = listOf(
        Regex("\\b\\d{6}\\b"),
        Regex("mã otp", RegexOption.IGNORE_CASE),
        Regex("otp", RegexOption.IGNORE_CASE),
        Regex("verification code", RegexOption.IGNORE_CASE),
        Regex("mã xác thực", RegexOption.IGNORE_CASE),
    )

    fun shouldSkip(payload: NotificationPayload): Boolean {
        if (!enabled) return false
        val text = payload.title + "\n" + payload.body
        return (defaultPatterns + customPatterns).any { it.containsMatchIn(text) }
    }
}
