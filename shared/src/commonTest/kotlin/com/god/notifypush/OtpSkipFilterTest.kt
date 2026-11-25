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

    private fun filter(settings: AppSettings = AppSettings()): OtpSkipFilter =
        OtpSkipFilter(settings)

    @Test
    fun vietnameseOtpIsSkipped() {
        assertTrue(filter().shouldSkip(payload("App", "Ma OTP cua ban la 123456. Khong chia se cho ai.")))
    }

    @Test
    fun vietnameseVerificationCodeIsSkipped() {
        assertTrue(filter().shouldSkip(payload("App", "Ma xac thuc cua ban: 123456")))
    }

    @Test
    fun englishVerificationCodeIsSkipped() {
        assertTrue(filter().shouldSkip(payload("App", "Your verification code is 123456")))
    }

    @Test
    fun vietnameseConfirmationCodeIsSkipped() {
        assertTrue(filter().shouldSkip(payload("App", "Ma xac nhan cua ban: 123456")))
    }

    @Test
    fun bareCodeWithoutOtpMarkerIsNotSkipped() {
        assertFalse(filter().shouldSkip(payload("App", "Your code is 123456")))
    }

    @Test
    fun sixDigitReferenceIsNotSkipped() {
        assertFalse(
            filter().shouldSkip(
                payload("App", "Giao dich thanh cong. Ma tham chieu: 123456"),
            ),
        )
    }

    @Test
    fun balanceChangeIsNotSkipped() {
        assertFalse(
            filter().shouldSkip(
                payload(
                    "Vietcombank",
                    "TK 1234567890: +500.000 VND luc 10:23 25/11/2026. So du: 12.345.678 VND.",
                ),
            ),
        )
    }

    @Test
    fun customPatternExtendsFilter() {
        val custom = filter(AppSettings(otpCustomPatterns = listOf("khuyen mai")))
        assertTrue(custom.shouldSkip(payload("App", "Khuyen mai lon hom nay")))
    }

    @Test
    fun disabledFilterForwardsOtp() {
        val off = filter(AppSettings(otpFilterEnabled = false))
        assertFalse(off.shouldSkip(payload("App", "Ma OTP cua ban la 123456.")))
    }

    @Test
    fun invalidCustomPatternIsIgnored() {
        val custom = filter(AppSettings(otpCustomPatterns = listOf("[unclosed")))
        assertFalse(custom.shouldSkip(payload("App", "Khuyen mai lon hom nay")))
        assertTrue(custom.shouldSkip(payload("App", "Ma OTP cua ban la 123456.")))
    }
}
