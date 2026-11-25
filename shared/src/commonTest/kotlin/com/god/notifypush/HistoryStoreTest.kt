package com.god.notifypush

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HistoryStoreTest {
    private val payload = TestPayloadBuilder.sample("Device")

    private fun entry(id: String, atMs: Long): HistoryEntry = HistoryEntry(
        id = id,
        payload = payload,
        results = listOf(DispatchResult("mail-tool", "Mail Tool", DispatchStatus.DELIVERED, httpCode = 202)),
        recordedAtEpochMs = atMs,
    )

    @Test
    fun recordAndRecentReturnsNewestFirst() = runTest {
        val store: HistoryStore = JsonFileHistoryStore(InMemoryFileStorage())
        store.record(entry("first", 1_000L))
        store.record(entry("second", 2_000L))
        val recent = store.recent()
        assertEquals(listOf("second", "first"), recent.map { it.id })
    }

    @Test
    fun recentRespectsLimit() = runTest {
        val store: HistoryStore = JsonFileHistoryStore(InMemoryFileStorage())
        store.record(entry("first", 1_000L))
        store.record(entry("second", 2_000L))
        assertEquals(listOf("second"), store.recent(limit = 1).map { it.id })
    }

    @Test
    fun clearEmptiesHistory() = runTest {
        val store: HistoryStore = JsonFileHistoryStore(InMemoryFileStorage())
        store.record(entry("first", 1_000L))
        store.clear()
        assertTrue(store.recent().isEmpty())
    }
}
