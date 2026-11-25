package com.god.notifypush

object OtpSkipFilter {
    var enabled: Boolean = true
    var customPatterns: List<Regex> = emptyList()

    private val defaultPatterns: List<Regex> = listOf(
        Regex("mã otp", RegexOption.IGNORE_CASE),
        Regex("ma otp", RegexOption.IGNORE_CASE),
        Regex("otp", RegexOption.IGNORE_CASE),
        Regex("verification code", RegexOption.IGNORE_CASE),
        Regex("security code", RegexOption.IGNORE_CASE),
        Regex("mã xác thực", RegexOption.IGNORE_CASE),
        Regex("ma xac thuc", RegexOption.IGNORE_CASE),
        Regex("mã xác nhận", RegexOption.IGNORE_CASE),
        Regex("ma xac nhan", RegexOption.IGNORE_CASE),
        Regex("mã bảo mật", RegexOption.IGNORE_CASE),
        Regex("ma bao mat", RegexOption.IGNORE_CASE),
    )

    fun shouldSkip(payload: NotificationPayload): Boolean {
        if (!enabled) return false
        val text = payload.rawText
        return (defaultPatterns + customPatterns).any { it.containsMatchIn(text) }
    }
}
