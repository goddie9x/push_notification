package com.god.pushnotification

import com.god.notifypush.DispatchStatus
import com.god.notifypush.Dispatcher
import com.god.notifypush.HistoryEntry
import com.god.notifypush.HistoryStore
import com.god.notifypush.NotificationPayload
import com.god.notifypush.OtpSkipFilter
import com.god.notifypush.RetryQueue
import java.util.UUID

class NotificationForwarder(
    private val ownPackage: String,
    private val dispatcher: Dispatcher,
    private val historyStore: HistoryStore,
    private val retryQueue: RetryQueue,
    private val isOtp: (NotificationPayload) -> Boolean = OtpSkipFilter::shouldSkip
) {

    suspend fun forward(payload: NotificationPayload, packageName: String) {
        if (packageName == ownPackage) return
        if (isOtp(payload)) return
        val results = dispatcher.dispatch(payload)
        historyStore.record(
            HistoryEntry(
                id = UUID.randomUUID().toString(),
                payload = payload,
                results = results,
                recordedAtEpochMs = System.currentTimeMillis()
            )
        )
        results.filter { it.status == DispatchStatus.FAILED }
            .forEach { retryQueue.enqueue(it.destinationId, payload) }
    }
}
