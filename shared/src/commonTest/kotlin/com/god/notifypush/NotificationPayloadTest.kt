package com.god.notifypush

import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotificationPayloadTest {
    private val payload = NotificationPayload.fromNotification(
        sourceApp = "Vietcombank",
        bundleId = "com.vietcombank.vcb",
        title = "Vietcombank",
        subtitle = "",
        body = "TK 1234567890: +500.000 VND. So du: 12.345.678 VND.",
        device = "Pixel 8",
    )

    @Test
    fun schemaDefaultsToNotifyPushV1() {
        assertEquals("notify-push/v1", payload.schema)
    }

    @Test
    fun rawTextDropsEmptyParts() {
        assertEquals(
            "Vietcombank\nTK 1234567890: +500.000 VND. So du: 12.345.678 VND.",
            payload.rawText,
        )
    }

    @Test
    fun rawTextKeepsSubtitleWhenPresent() {
        val withSubtitle = NotificationPayload.fromNotification(
            sourceApp = "App",
            bundleId = "com.example.app",
            title = "Title",
            subtitle = "Subtitle",
            body = "Body",
            device = "Device",
        )
        assertEquals("Title\nSubtitle\nBody", withSubtitle.rawText)
    }

    @Test
    fun receivedAtIsIso8601() {
        Instant.parse(payload.receivedAt)
    }

    @Test
    fun serializedFormHasNineFieldsAndNoParsedFields() {
        val json = Json.parseToJsonElement(notifyJson.encodeToString(payload)).toString()
        listOf(
            "schema", "sourceApp", "bundleId", "title", "subtitle",
            "body", "receivedAt", "rawText", "device",
        ).forEach { field -> assertTrue(json.contains("\"$field\""), "missing field $field") }
        listOf("amount", "balance", "direction", "bank").forEach { field ->
            assertFalse(json.contains("\"$field\""), "parsed field leaked: $field")
        }
    }

    @Test
    fun samplePayloadIsClearlyMarkedTest() {
        val sample = TestPayloadBuilder.sample("TestDevice")
        assertEquals("Test notification", sample.title)
        assertEquals("This is a test notification from NotifyPush", sample.body)
        assertEquals("NotifyPush", sample.sourceApp)
        assertEquals("com.god.pushnotification", sample.bundleId)
        assertEquals("TestDevice", sample.device)
    }
}
