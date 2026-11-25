package com.god.pushnotification

import org.junit.Assert.assertEquals
import org.junit.Test

class PayloadExtractorTest {

    @Test
    fun extractsTitleSubtitleAndBody() {
        val payload = PayloadExtractor.fromNotification(
            packageName = "com.vietcombank.vcb",
            appName = "Vietcombank",
            title = "Vietcombank",
            subtitle = "Alert",
            body = "TK 1234567890: +500.000 VND"
        )
        assertEquals("com.vietcombank.vcb", payload.bundleId)
        assertEquals("Vietcombank", payload.sourceApp)
        assertEquals("Vietcombank", payload.title)
        assertEquals("Alert", payload.subtitle)
        assertEquals("TK 1234567890: +500.000 VND", payload.body)
        assertEquals("android", payload.device)
        assertEquals("Vietcombank\nAlert\nTK 1234567890: +500.000 VND", payload.rawText)
    }

    @Test
    fun nullFieldsBecomeEmptyStrings() {
        val payload = PayloadExtractor.fromNotification(
            packageName = "com.example.app",
            appName = "Example",
            title = null,
            subtitle = null,
            body = null
        )
        assertEquals("", payload.title)
        assertEquals("", payload.subtitle)
        assertEquals("", payload.body)
        assertEquals("", payload.rawText)
    }

    @Test
    fun emptySubtitleIsOmittedFromRawText() {
        val payload = PayloadExtractor.fromNotification(
            packageName = "com.example.app",
            appName = "Example",
            title = "Title",
            subtitle = "",
            body = "Body"
        )
        assertEquals("Title\nBody", payload.rawText)
    }

    @Test
    fun nullBodyYieldsEmptyBody() {
        val payload = PayloadExtractor.fromNotification(
            packageName = "com.example.app",
            appName = "Example",
            title = "Title",
            subtitle = null,
            body = null
        )
        assertEquals("", payload.body)
        assertEquals("Title", payload.rawText)
    }
}
