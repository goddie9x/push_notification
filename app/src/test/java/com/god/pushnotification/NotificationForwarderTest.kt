package com.god.pushnotification

import com.god.notifypush.DispatchResult
import com.god.notifypush.DispatchStatus
import com.god.notifypush.Dispatcher
import com.god.notifypush.HistoryEntry
import com.god.notifypush.HistoryStore
import com.god.notifypush.NotificationPayload
import com.god.notifypush.RetryQueue
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.whenever
import org.mockito.kotlin.verifyBlocking

class NotificationForwarderTest {

    private val ownPackage = "com.god.pushnotification"

    private fun payload(title: String, body: String): NotificationPayload =
        NotificationPayload.fromNotification(
            sourceApp = "Vietcombank",
            bundleId = "com.vietcombank.vcb",
            title = title,
            subtitle = "",
            body = body,
            device = "android"
        )

    private fun result(status: DispatchStatus): DispatchResult =
        DispatchResult(
            destinationId = "mail-tool",
            destinationName = "mail-tool",
            status = status,
            httpCode = 202,
            error = ""
        )

    private fun forwarder(
        dispatcher: Dispatcher,
        historyStore: HistoryStore,
        retryQueue: RetryQueue
    ) = NotificationForwarder(ownPackage, dispatcher, historyStore, retryQueue)

    @Test
    fun normalPayloadIsDispatchedAndRecorded() = runTest {
        val payload = payload("Vietcombank", "TK 1234567890: +500.000 VND luc 10:23 25/11/2026.")
        val results = listOf(result(DispatchStatus.DELIVERED))
        val dispatcher = mock<Dispatcher>()
        whenever(dispatcher.dispatch(any())) doReturn results
        val historyStore = mock<HistoryStore>()
        val retryQueue = mock<RetryQueue>()

        forwarder(dispatcher, historyStore, retryQueue)
            .forward(payload, "com.vietcombank.vcb")

        verifyBlocking(dispatcher) { dispatch(payload) }
        val captor = argumentCaptor<HistoryEntry>()
        verifyBlocking(historyStore) { record(captor.capture()) }
        assertEquals(payload, captor.firstValue.payload)
        assertEquals(results, captor.firstValue.results)
        verifyBlocking(retryQueue, never()) { enqueue(any(), any()) }
    }

    @Test
    fun failedResultIsEnqueuedForRetry() = runTest {
        val payload = payload("Vietcombank", "TK 1234567890: +500.000 VND.")
        val failed = DispatchResult("mail-tool", "mail-tool", DispatchStatus.FAILED, 500, "boom")
        val dispatcher = mock<Dispatcher>()
        whenever(dispatcher.dispatch(any())) doReturn listOf(failed)
        val historyStore = mock<HistoryStore>()
        val retryQueue = mock<RetryQueue>()

        forwarder(dispatcher, historyStore, retryQueue)
            .forward(payload, "com.vietcombank.vcb")

        verifyBlocking(retryQueue) { enqueue("mail-tool", payload) }
        verifyBlocking(historyStore) { record(any()) }
    }

    @Test
    fun rejectedResultIsRecordedButNotRetried() = runTest {
        val payload = payload("Vietcombank", "TK 1234567890: +500.000 VND.")
        val rejected = DispatchResult("mail-tool", "mail-tool", DispatchStatus.REJECTED, 401, "")
        val dispatcher = mock<Dispatcher>()
        whenever(dispatcher.dispatch(any())) doReturn listOf(rejected)
        val historyStore = mock<HistoryStore>()
        val retryQueue = mock<RetryQueue>()

        forwarder(dispatcher, historyStore, retryQueue)
            .forward(payload, "com.vietcombank.vcb")

        verifyBlocking(historyStore) { record(any()) }
        verifyBlocking(retryQueue, never()) { enqueue(any(), any()) }
    }

    @Test
    fun ownPackageIsNeverForwarded() = runTest {
        val payload = payload("NotifyPush", "Test notification")
        val dispatcher = mock<Dispatcher>()
        val historyStore = mock<HistoryStore>()
        val retryQueue = mock<RetryQueue>()

        forwarder(dispatcher, historyStore, retryQueue).forward(payload, ownPackage)

        verifyBlocking(dispatcher, never()) { dispatch(any()) }
        verifyBlocking(historyStore, never()) { record(any()) }
    }

    @Test
    fun otpPayloadIsNeverForwarded() = runTest {
        val payload = payload("Vietcombank", "Ma OTP cua ban la 123456. Khong chia se cho ai.")
        val dispatcher = mock<Dispatcher>()
        val historyStore = mock<HistoryStore>()
        val retryQueue = mock<RetryQueue>()

        forwarder(dispatcher, historyStore, retryQueue)
            .forward(payload, "com.vietcombank.vcb")

        verifyBlocking(dispatcher, never()) { dispatch(any()) }
        verifyBlocking(historyStore, never()) { record(any()) }
    }

    @Test
    fun otpPayloadIsForwardedWhenFilterIsOff() = runTest {
        val payload = payload("Vietcombank", "Ma OTP cua ban la 123456. Khong chia se cho ai.")
        val dispatcher = mock<Dispatcher>()
        whenever(dispatcher.dispatch(any())) doReturn listOf(result(DispatchStatus.DELIVERED))
        val historyStore = mock<HistoryStore>()
        val retryQueue = mock<RetryQueue>()
        val forwarder = NotificationForwarder(
            ownPackage,
            dispatcher,
            historyStore,
            retryQueue,
            isOtp = { false },
        )

        forwarder.forward(payload, "com.vietcombank.vcb")

        verifyBlocking(dispatcher) { dispatch(payload) }
    }

    @Test
    fun balanceChangeWithLongAccountNumberIsForwarded() = runTest {
        val payload = payload(
            "Vietcombank",
            "TK 1234567890: +500.000 VND luc 10:23 25/11/2026. So du: 12.345.678 VND."
        )
        val dispatcher = mock<Dispatcher>()
        whenever(dispatcher.dispatch(any())) doReturn listOf(result(DispatchStatus.DELIVERED))
        val historyStore = mock<HistoryStore>()
        val retryQueue = mock<RetryQueue>()

        forwarder(dispatcher, historyStore, retryQueue)
            .forward(payload, "com.vietcombank.vcb")

        verifyBlocking(dispatcher) { dispatch(payload) }
    }
}
