package com.god.notifypush

import kotlinx.datetime.Clock
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

internal class JsonFileHistoryStore(
    private val storage: FileStorage,
    private val loadSettings: suspend () -> AppSettings = { AppSettings() },
) : HistoryStore {
    override suspend fun record(entry: HistoryEntry) {
        val settings = loadSettings()
        if (!settings.historyEnabled) return
        save(listOf(entry) + loadEntries(settings))
    }

    override suspend fun recent(limit: Int): List<HistoryEntry> {
        val settings = loadSettings()
        if (!settings.historyEnabled) return emptyList()
        val stored = loadRaw()
        val entries = stored.filter { it.recordedAtEpochMs >= cutoffMs(settings) }
        if (entries.size < stored.size) save(entries)
        return entries.take(limit)
    }

    override suspend fun clear() {
        save(emptyList())
    }

    private fun loadRaw(): List<HistoryEntry> {
        val raw = storage.readText(FILE_NAME) ?: return emptyList()
        return runCatching { notifyJson.decodeFromString<List<HistoryEntry>>(raw) }
            .getOrElse { emptyList() }
    }

    private fun cutoffMs(settings: AppSettings): Long =
        Clock.System.now().toEpochMilliseconds() -
            settings.historyRetentionDays * MILLIS_PER_DAY

    private fun loadEntries(settings: AppSettings): List<HistoryEntry> =
        loadRaw().filter { it.recordedAtEpochMs >= cutoffMs(settings) }

    private fun save(entries: List<HistoryEntry>) {
        storage.writeText(FILE_NAME, notifyJson.encodeToString(entries))
    }

    private companion object {
        const val FILE_NAME = "history.json"
        const val MILLIS_PER_DAY = 86_400_000L
    }
}

fun createHistoryStore(
    directoryPath: String,
    loadSettings: suspend () -> AppSettings = { AppSettings() },
): HistoryStore = JsonFileHistoryStore(createFileStorage(directoryPath), loadSettings)
