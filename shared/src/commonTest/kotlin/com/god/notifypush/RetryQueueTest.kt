package com.god.notifypush

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RetryQueueTest {
    private val payload = TestPayloadBuilder.sample("Device")

    @Test
    fun backoffDoublesAndCapsAtOneHour() {
        assertEquals(30_000L, nextBackoffMs(0))
        assertEquals(60_000L, nextBackoffMs(1))
        assertEquals(120_000L, nextBackoffMs(2))
        assertEquals(3_600_000L, nextBackoffMs(7))
        assertEquals(3_600_000L, nextBackoffMs(100))
    }

    @Test
    fun failedItemPersistsThenSucceedsAndIsRemoved() = runTest {
        val queue: RetryQueue = JsonFileRetryQueue(InMemoryFileStorage())
        queue.enqueue("mail-tool", payload)
        val pending = queue.pending()
        assertEquals(1, pending.size)
        assertEquals("mail-tool", pending.single().destinationId)
        assertEquals(0, pending.single().attempts)
        assertTrue(pending.single().nextRetryAtEpochMs > 0)

        queue.markAttempt(pending.single().id, nextRetryAtEpochMs = 9_999L)
        val retried = queue.pending().single()
        assertEquals(1, retried.attempts)
        assertEquals(9_999L, retried.nextRetryAtEpochMs)

        queue.remove(retried.id)
        assertTrue(queue.pending().isEmpty())
    }
}
