package com.god.notifypush

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HistoryStoreTest {
    private val payload = TestPayloadBuilder.sample("Device")
    private val now = Clock.System.now().toEpochMilliseconds()

    private fun entry(id: String, atMs: Long): HistoryEntry = HistoryEntry(
        id = id,
        payload = payload,
        results = listOf(DispatchResult("mail-tool", "Mail Tool", DispatchStatus.DELIVERED, httpCode = 202)),
        recordedAtEpochMs = atMs,
    )

    @Test
    fun recordAndRecentReturnsNewestFirst() = runTest {
        val store: HistoryStore = JsonFileHistoryStore(InMemoryFileStorage())
        store.record(entry("first", now - 1_000L))
        store.record(entry("second", now))
        val recent = store.recent()
        assertEquals(listOf("second", "first"), recent.map { it.id })
    }

    @Test
    fun recentRespectsLimit() = runTest {
        val store: HistoryStore = JsonFileHistoryStore(InMemoryFileStorage())
        store.record(entry("first", now - 1_000L))
        store.record(entry("second", now))
        assertEquals(listOf("second"), store.recent(limit = 1).map { it.id })
    }

    @Test
    fun clearEmptiesHistory() = runTest {
        val store: HistoryStore = JsonFileHistoryStore(InMemoryFileStorage())
        store.record(entry("first", now))
        store.clear()
        assertTrue(store.recent().isEmpty())
    }

    @Test
    fun disabledHistoryStoresNothing() = runTest {
        val store: HistoryStore = JsonFileHistoryStore(
            InMemoryFileStorage(),
            loadSettings = { AppSettings(historyEnabled = false) },
        )
        store.record(entry("first", now))
        assertTrue(store.recent().isEmpty())
    }

    @Test
    fun entriesOlderThanRetentionArePruned() = runTest {
        val store: HistoryStore = JsonFileHistoryStore(
            InMemoryFileStorage(),
            loadSettings = { AppSettings(historyRetentionDays = 7) },
        )
        store.record(entry("old", now - 8 * 86_400_000L))
        store.record(entry("fresh", now))
        assertEquals(listOf("fresh"), store.recent().map { it.id })
    }
}
