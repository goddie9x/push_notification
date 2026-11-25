package com.god.notifypush

import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlin.random.Random

@Serializable
data class QueuedDispatch(
    val id: String,
    val destinationId: String,
    val payload: NotificationPayload,
    val attempts: Int = 0,
    val nextRetryAtEpochMs: Long,
)

interface RetryQueue {
    suspend fun enqueue(destinationId: String, payload: NotificationPayload)
    suspend fun pending(): List<QueuedDispatch>
    suspend fun remove(id: String)
    suspend fun markAttempt(id: String, nextRetryAtEpochMs: Long)
}

fun pendingForAutoRetry(
    items: List<QueuedDispatch>,
    settings: AppSettings,
    nowEpochMs: Long,
): List<QueuedDispatch> =
    if (!settings.autoRetryEnabled) emptyList()
    else items.filter { it.nextRetryAtEpochMs <= nowEpochMs }

fun nextBackoffMs(attempts: Int): Long {
    val delay = 30_000L * (1L shl attempts.coerceIn(0, 10))
    return delay.coerceAtMost(3_600_000L)
}

internal class JsonFileRetryQueue(private val storage: FileStorage) : RetryQueue {
    override suspend fun enqueue(destinationId: String, payload: NotificationPayload) {
        val now = Clock.System.now().toEpochMilliseconds()
        val item = QueuedDispatch(
            id = "$now-${Random.nextLong()}",
            destinationId = destinationId,
            payload = payload,
            attempts = 0,
            nextRetryAtEpochMs = now + nextBackoffMs(0),
        )
        save(loadItems() + item)
    }

    override suspend fun pending(): List<QueuedDispatch> = loadItems()

    override suspend fun remove(id: String) {
        save(loadItems().filter { it.id != id })
    }

    override suspend fun markAttempt(id: String, nextRetryAtEpochMs: Long) {
        save(loadItems().map {
            if (it.id == id) it.copy(attempts = it.attempts + 1, nextRetryAtEpochMs = nextRetryAtEpochMs) else it
        })
    }

    private fun loadItems(): List<QueuedDispatch> {
        val raw = storage.readText(FILE_NAME) ?: return emptyList()
        return runCatching { notifyJson.decodeFromString<List<QueuedDispatch>>(raw) }.getOrElse { emptyList() }
    }

    private fun save(items: List<QueuedDispatch>) {
        storage.writeText(FILE_NAME, notifyJson.encodeToString(items))
    }

    private companion object {
        const val FILE_NAME = "retry-queue.json"
    }
}

fun createRetryQueue(directoryPath: String): RetryQueue =
    JsonFileRetryQueue(createFileStorage(directoryPath))
