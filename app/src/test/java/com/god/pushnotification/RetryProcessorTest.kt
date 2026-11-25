package com.god.pushnotification

import com.god.notifypush.AppSettings
import com.god.notifypush.DispatchResult
import com.god.notifypush.DispatchStatus
import com.god.notifypush.Dispatcher
import com.god.notifypush.QueuedDispatch
import com.god.notifypush.RetryQueue
import com.god.notifypush.TestPayloadBuilder
import com.god.notifypush.nextBackoffMs
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.whenever
import org.mockito.kotlin.verifyBlocking

class RetryProcessorTest {

    private val payload = TestPayloadBuilder.sample("android")
    private val now = 1_000_000L

    private fun queued(
        id: String = "q1",
        attempts: Int = 0,
        nextRetryAtEpochMs: Long = 0L
    ) = QueuedDispatch(
        id = id,
        destinationId = "mail-tool",
        payload = payload,
        attempts = attempts,
        nextRetryAtEpochMs = nextRetryAtEpochMs
    )

    private fun delivered() =
        DispatchResult("mail-tool", "mail-tool", DispatchStatus.DELIVERED, 202, "")

    private fun failed() =
        DispatchResult("mail-tool", "mail-tool", DispatchStatus.FAILED, 500, "boom")

    @Test
    fun deliveredItemIsRemoved() = runTest {
        val queue = mock<RetryQueue>()
        whenever(queue.pending()) doReturn listOf(queued())
        val dispatcher = mock<Dispatcher>()
        whenever(dispatcher.dispatch(any())) doReturn listOf(delivered())

        val outcome = RetryProcessor(queue, dispatcher).processPending(now)

        assertEquals(0, outcome.failedCount)
        verifyBlocking(queue) { remove("q1") }
        verifyBlocking(queue, never()) { markAttempt(any(), any()) }
    }

    @Test
    fun rejectedItemIsRemoved() = runTest {
        val rejected = DispatchResult("mail-tool", "mail-tool", DispatchStatus.REJECTED, 401, "")
        val queue = mock<RetryQueue>()
        whenever(queue.pending()) doReturn listOf(queued())
        val dispatcher = mock<Dispatcher>()
        whenever(dispatcher.dispatch(any())) doReturn listOf(rejected)

        val outcome = RetryProcessor(queue, dispatcher).processPending(now)

        assertEquals(0, outcome.failedCount)
        verifyBlocking(queue) { remove("q1") }
    }

    @Test
    fun failedItemIsMarkedWithNextBackoff() = runTest {
        val queue = mock<RetryQueue>()
        whenever(queue.pending()) doReturn listOf(queued(attempts = 2))
        val dispatcher = mock<Dispatcher>()
        whenever(dispatcher.dispatch(any())) doReturn listOf(failed())

        val outcome = RetryProcessor(queue, dispatcher).processPending(now)

        assertEquals(1, outcome.failedCount)
        verifyBlocking(queue) { markAttempt("q1", now + nextBackoffMs(2)) }
        verifyBlocking(queue, never()) { remove(any()) }
    }

    @Test
    fun missingResultIsMarkedAsFailure() = runTest {
        val queue = mock<RetryQueue>()
        whenever(queue.pending()) doReturn listOf(queued())
        val dispatcher = mock<Dispatcher>()
        whenever(dispatcher.dispatch(any())) doReturn emptyList()

        val outcome = RetryProcessor(queue, dispatcher).processPending(now)

        assertEquals(1, outcome.failedCount)
        verifyBlocking(queue) { markAttempt("q1", now + nextBackoffMs(0)) }
    }

    @Test
    fun notYetDueItemIsSkipped() = runTest {
        val queue = mock<RetryQueue>()
        whenever(queue.pending()) doReturn listOf(queued(nextRetryAtEpochMs = now + 60_000L))
        val dispatcher = mock<Dispatcher>()

        val outcome = RetryProcessor(queue, dispatcher).processPending(now)

        assertEquals(0, outcome.failedCount)
        verifyBlocking(dispatcher, never()) { dispatch(any()) }
        verifyBlocking(queue, never()) { remove(any()) }
        verifyBlocking(queue, never()) { markAttempt(any(), any()) }
    }

    @Test
    fun emptyQueueDoesNothing() = runTest {
        val queue = mock<RetryQueue>()
        whenever(queue.pending()) doReturn emptyList()
        val dispatcher = mock<Dispatcher>()

        val outcome = RetryProcessor(queue, dispatcher).processPending(now)

        assertEquals(0, outcome.failedCount)
        verifyBlocking(dispatcher, never()) { dispatch(any()) }
    }

    @Test
    fun autoRetryOffLeavesQueueUntouched() = runTest {
        val queue = mock<RetryQueue>()
        whenever(queue.pending()) doReturn listOf(queued())
        val dispatcher = mock<Dispatcher>()
        val processor = RetryProcessor(
            queue,
            dispatcher,
            loadSettings = { AppSettings(autoRetryEnabled = false) },
        )

        val outcome = processor.processPending(now)

        assertEquals(0, outcome.failedCount)
        verifyBlocking(dispatcher, never()) { dispatch(any()) }
        verifyBlocking(queue, never()) { remove(any()) }
        verifyBlocking(queue, never()) { markAttempt(any(), any()) }
    }

    @Test
    fun forcedRetryRunsEvenWhenAutoRetryOff() = runTest {
        val queue = mock<RetryQueue>()
        whenever(queue.pending()) doReturn listOf(queued())
        val dispatcher = mock<Dispatcher>()
        whenever(dispatcher.dispatch(any())) doReturn listOf(delivered())
        val processor = RetryProcessor(
            queue,
            dispatcher,
            loadSettings = { AppSettings(autoRetryEnabled = false) },
        )

        val outcome = processor.processPending(now, force = true)

        assertEquals(0, outcome.failedCount)
        verifyBlocking(dispatcher) { dispatch(any()) }
        verifyBlocking(queue) { remove("q1") }
    }
}
