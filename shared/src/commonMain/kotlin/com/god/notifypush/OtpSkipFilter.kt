package com.god.notifypush

class OtpSkipFilter(private val settings: AppSettings) {

    fun shouldSkip(payload: NotificationPayload): Boolean {
        if (!settings.otpFilterEnabled) return false
        val text = payload.rawText
        return (defaultPatterns + customPatterns()).any { it.containsMatchIn(text) }
    }

    private fun customPatterns(): List<Regex> =
        settings.otpCustomPatterns.mapNotNull { pattern ->
            runCatching { Regex(pattern, RegexOption.IGNORE_CASE) }.getOrNull()
        }

    private companion object {
        val defaultPatterns: List<Regex> = listOf(
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
    }
}
