package com.god.pushnotification

import com.god.notifypush.nextBackoffMs
import org.junit.Assert.assertEquals
import org.junit.Test

class RetryBackoffTest {

    @Test
    fun backoffDoublesFromThirtySeconds() {
        assertEquals(30_000L, nextBackoffMs(0))
        assertEquals(60_000L, nextBackoffMs(1))
        assertEquals(120_000L, nextBackoffMs(2))
        assertEquals(240_000L, nextBackoffMs(3))
        assertEquals(1_920_000L, nextBackoffMs(6))
    }

    @Test
    fun backoffCapsAtOneHour() {
        assertEquals(3_600_000L, nextBackoffMs(7))
        assertEquals(3_600_000L, nextBackoffMs(100))
    }

    @Test
    fun negativeAttemptsBehaveAsZero() {
        assertEquals(30_000L, nextBackoffMs(-1))
    }
}
