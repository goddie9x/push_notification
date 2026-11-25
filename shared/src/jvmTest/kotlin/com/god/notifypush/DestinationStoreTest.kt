package com.god.notifypush

import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DestinationStoreTest {
    @Test
    fun destinationsRoundTripThroughRealFile() = runTest {
        val dir = Files.createTempDirectory("notify-push-destinations").toString()
        val store = createDestinationStore(dir)
        assertTrue(store.load().isEmpty())
        val destinations = listOf(
            Destination(id = "mail-tool", name = "Mail Tool", url = "https://mail.hmt9x.dev/api/ingest/notification"),
            Destination(id = "other", name = "Other", url = "https://other.example/ingest", enabled = false),
        )
        store.save(destinations)
        assertEquals(destinations, createDestinationStore(dir).load())
    }

    @Test
    fun retryQueueAndHistoryRoundTripThroughRealFiles() = runTest {
        val dir = Files.createTempDirectory("notify-push-queue-history").toString()
        val queue = createRetryQueue(dir)
        queue.enqueue("mail-tool", TestPayloadBuilder.sample("Device"))
        assertEquals(1, createRetryQueue(dir).pending().size)

        val history = createHistoryStore(dir)
        history.record(
            HistoryEntry(
                id = "entry-1",
                payload = TestPayloadBuilder.sample("Device"),
                results = listOf(DispatchResult("mail-tool", "Mail Tool", DispatchStatus.DELIVERED, httpCode = 202)),
                recordedAtEpochMs = 1_000L,
            ),
        )
        assertEquals(listOf("entry-1"), createHistoryStore(dir).recent().map { it.id })
    }

    @Test
    fun jvmHttpClientFactoryBuilds() {
        httpClientFactory().close()
    }
}
