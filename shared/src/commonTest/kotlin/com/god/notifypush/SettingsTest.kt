package com.god.notifypush

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsTest {
    private val payload = TestPayloadBuilder.sample("Device")

    private fun otpPayload(): NotificationPayload =
        NotificationPayload.fromNotification(
            sourceApp = "App",
            bundleId = "com.example.app",
            title = "App",
            subtitle = "",
            body = "Ma OTP cua ban la 123456.",
            device = "Device",
        )

    @Test
    fun defaultsAreSafe() {
        val settings = AppSettings()
        assertTrue(settings.otpFilterEnabled)
        assertTrue(settings.historyEnabled)
        assertEquals(30, settings.historyRetentionDays)
        assertTrue(settings.autoRetryEnabled)
        assertEquals(15, settings.retryIntervalMinutes)
        assertTrue(settings.otpCustomPatterns.isEmpty())
    }

    @Test
    fun settingsPersistAndReload() = runTest {
        val store: SettingsStore = JsonFileSettingsStore(InMemoryFileStorage())
        val saved = AppSettings(
            otpFilterEnabled = false,
            otpCustomPatterns = listOf("khuyen mai"),
            historyEnabled = false,
            historyRetentionDays = 90,
            autoRetryEnabled = false,
            retryIntervalMinutes = 5,
        )
        store.save(saved)
        assertEquals(saved, store.load())
    }

    @Test
    fun missingFileLoadsDefaults() = runTest {
        val store: SettingsStore = JsonFileSettingsStore(InMemoryFileStorage())
        assertEquals(AppSettings(), store.load())
    }

    @Test
    fun outOfRangeRetentionAndIntervalNormalizeToDefaults() = runTest {
        val store: SettingsStore = JsonFileSettingsStore(InMemoryFileStorage())
        store.save(AppSettings(historyRetentionDays = 45, retryIntervalMinutes = 10))
        val loaded = store.load()
        assertEquals(30, loaded.historyRetentionDays)
        assertEquals(15, loaded.retryIntervalMinutes)
    }

    @Test
    fun disabledDestinationReceivesNothing() = runTest {
        val engine = MockEngine { respond("{}", HttpStatusCode.OK) }
        val client = HttpClient(engine) { install(ContentNegotiation) { json() } }
        val dispatcher = Dispatcher(
            client,
            FakeDestinationStore(
                listOf(
                    Destination(
                        id = "a",
                        name = "a",
                        url = "https://a.example/ingest",
                        enabled = false,
                    ),
                ),
            ),
        )
        assertTrue(dispatcher.dispatch(payload).isEmpty())
    }

    @Test
    fun otpFilterOnBlocksCodeMessage() {
        assertTrue(OtpSkipFilter(AppSettings()).shouldSkip(otpPayload()))
    }

    @Test
    fun otpFilterOffForwardsCodeMessage() {
        assertFalse(OtpSkipFilter(AppSettings(otpFilterEnabled = false)).shouldSkip(otpPayload()))
    }

    @Test
    fun customPatternBlocksMatchingMessage() {
        val filter = OtpSkipFilter(AppSettings(otpCustomPatterns = listOf("khuyen mai")))
        val promo = NotificationPayload.fromNotification(
            sourceApp = "App",
            bundleId = "com.example.app",
            title = "App",
            subtitle = "",
            body = "Khuyen mai lon hom nay",
            device = "Device",
        )
        assertTrue(filter.shouldSkip(promo))
    }

    @Test
    fun autoRetryOffLeavesQueueUntouched() {
        val queued = QueuedDispatch(
            id = "q1",
            destinationId = "mail-tool",
            payload = payload,
            attempts = 0,
            nextRetryAtEpochMs = 0L,
        )
        val now = 1_000_000L
        assertTrue(pendingForAutoRetry(listOf(queued), AppSettings(autoRetryEnabled = false), now).isEmpty())
        assertEquals(
            listOf(queued),
            pendingForAutoRetry(listOf(queued), AppSettings(autoRetryEnabled = true), now),
        )
    }

    @Test
    fun notYetDueItemIsNotSelectedForAutoRetry() {
        val queued = QueuedDispatch(
            id = "q1",
            destinationId = "mail-tool",
            payload = payload,
            attempts = 0,
            nextRetryAtEpochMs = 2_000_000L,
        )
        assertTrue(pendingForAutoRetry(listOf(queued), AppSettings(), 1_000_000L).isEmpty())
    }
}
