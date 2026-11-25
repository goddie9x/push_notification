package com.god.pushnotification

import com.god.notifypush.AppSettings
import com.god.notifypush.DispatchStatus
import com.god.notifypush.Dispatcher
import com.god.notifypush.RetryQueue
import com.god.notifypush.nextBackoffMs
import com.god.notifypush.pendingForAutoRetry

data class RetryOutcome(val failedCount: Int) {
    val hasFailures: Boolean get() = failedCount > 0
}

class RetryProcessor(
    private val queue: RetryQueue,
    private val dispatcher: Dispatcher,
    private val loadSettings: suspend () -> AppSettings = { AppSettings() }
) {

    suspend fun processPending(
        nowEpochMs: Long = System.currentTimeMillis(),
        force: Boolean = false,
    ): RetryOutcome {
        val due = if (force) {
            queue.pending()
        } else {
            pendingForAutoRetry(queue.pending(), loadSettings(), nowEpochMs)
        }
        var failed = 0
        for (item in due) {
            val result = dispatcher.dispatch(item.payload)
                .firstOrNull { it.destinationId == item.destinationId }
            if (result != null && result.status != DispatchStatus.FAILED) {
                queue.remove(item.id)
            } else {
                queue.markAttempt(item.id, nowEpochMs + nextBackoffMs(item.attempts))
                failed++
            }
        }
        return RetryOutcome(failed)
    }
}
