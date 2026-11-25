package com.god.notifypush

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

@Serializable
data class HistoryEntry(
    val id: String,
    val payload: NotificationPayload,
    val results: List<DispatchResult>,
    val recordedAtEpochMs: Long,
)

interface HistoryStore {
    suspend fun record(entry: HistoryEntry)
    suspend fun recent(limit: Int = 100): List<HistoryEntry>
    suspend fun clear()
}

internal class JsonFileHistoryStore(private val storage: FileStorage) : HistoryStore {
    override suspend fun record(entry: HistoryEntry) {
        save(listOf(entry) + loadEntries())
    }

    override suspend fun recent(limit: Int): List<HistoryEntry> =
        loadEntries().take(limit)

    override suspend fun clear() {
        save(emptyList())
    }

    private fun loadEntries(): List<HistoryEntry> {
        val raw = storage.readText(FILE_NAME) ?: return emptyList()
        return runCatching { notifyJson.decodeFromString<List<HistoryEntry>>(raw) }.getOrElse { emptyList() }
    }

    private fun save(entries: List<HistoryEntry>) {
        storage.writeText(FILE_NAME, notifyJson.encodeToString(entries))
    }

    private companion object {
        const val FILE_NAME = "history.json"
    }
}

fun createHistoryStore(directoryPath: String): HistoryStore =
    JsonFileHistoryStore(createFileStorage(directoryPath))
