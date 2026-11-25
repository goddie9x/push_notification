package com.god.notifypush

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DestinationTest {
    private val destination = Destination(
        id = "mail-tool",
        name = "Mail Tool",
        url = "https://mail.hmt9x.dev/api/ingest/notification",
        appsFilter = listOf("com.vietcombank.vcb", "MB Bank"),
    )

    @Test
    fun emptyFilterMatchesAllApps() {
        val open = destination.copy(appsFilter = emptyList())
        assertTrue(open.matches("com.any.app", "Any App"))
    }

    @Test
    fun filterMatchesBundleId() {
        assertTrue(destination.matches("com.vietcombank.vcb", "Something Else"))
    }

    @Test
    fun filterMatchesAppName() {
        assertTrue(destination.matches("com.other.app", "MB Bank"))
    }

    @Test
    fun filterRejectsUnlistedApp() {
        assertFalse(destination.matches("com.other.app", "Other App"))
    }
}
