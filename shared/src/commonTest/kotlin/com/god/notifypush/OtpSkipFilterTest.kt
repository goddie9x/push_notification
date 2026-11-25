package com.god.notifypush

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OtpSkipFilterTest {
    private fun payload(title: String, body: String): NotificationPayload =
        NotificationPayload.fromNotification(
            sourceApp = "App",
            bundleId = "com.example.app",
            title = title,
            subtitle = "",
            body = body,
            device = "Device",
        )

    @Test
    fun vietnameseOtpIsSkipped() {
        assertTrue(OtpSkipFilter.shouldSkip(payload("App", "Ma OTP cua ban la 123456. Khong chia se cho ai.")))
    }

    @Test
    fun vietnameseVerificationCodeIsSkipped() {
        assertTrue(OtpSkipFilter.shouldSkip(payload("App", "Ma xac thuc cua ban: 123456")))
    }

    @Test
    fun englishVerificationCodeIsSkipped() {
        assertTrue(OtpSkipFilter.shouldSkip(payload("App", "Your verification code is 123456")))
    }

    @Test
    fun vietnameseConfirmationCodeIsSkipped() {
        assertTrue(OtpSkipFilter.shouldSkip(payload("App", "Ma xac nhan cua ban: 123456")))
    }

    @Test
    fun bareCodeWithoutOtpMarkerIsNotSkipped() {
        assertFalse(OtpSkipFilter.shouldSkip(payload("App", "Your code is 123456")))
    }

    @Test
    fun sixDigitReferenceIsNotSkipped() {
        assertFalse(
            OtpSkipFilter.shouldSkip(
                payload("App", "Giao dich thanh cong. Ma tham chieu: 123456"),
            ),
        )
    }

    @Test
    fun balanceChangeIsNotSkipped() {
        assertFalse(
            OtpSkipFilter.shouldSkip(
                payload(
                    "Vietcombank",
                    "TK 1234567890: +500.000 VND luc 10:23 25/11/2026. So du: 12.345.678 VND.",
                ),
            ),
        )
    }

    @Test
    fun customPatternExtendsFilter() {
        OtpSkipFilter.customPatterns = listOf(Regex("khuyen mai", RegexOption.IGNORE_CASE))
        try {
            assertTrue(OtpSkipFilter.shouldSkip(payload("App", "Khuyen mai lon hom nay")))
        } finally {
            OtpSkipFilter.customPatterns = emptyList()
        }
    }
}
