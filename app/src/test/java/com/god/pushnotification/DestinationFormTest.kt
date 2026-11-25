package com.god.pushnotification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DestinationFormTest {

    @Test
    fun blankUrlIsRejected() {
        assertNotNull(DestinationForm.urlError(""))
        assertNotNull(DestinationForm.urlError("   "))
    }

    @Test
    fun nonHttpUrlIsRejected() {
        assertNotNull(DestinationForm.urlError("ftp://example.com/hook"))
        assertNotNull(DestinationForm.urlError("mail.hmt9x.dev/api/ingest/notification"))
    }

    @Test
    fun httpAndHttpsUrlsAreAccepted() {
        assertNull(DestinationForm.urlError("https://mail.hmt9x.dev/api/ingest/notification"))
        assertNull(DestinationForm.urlError("http://localhost:8080/hook"))
    }

    @Test
    fun appsFilterSplitsTrimsAndDropsEmpties() {
        assertEquals(
            listOf("com.vietcombank.vcb", "MB Bank"),
            DestinationForm.parseAppsFilter("com.vietcombank.vcb, MB Bank , ,")
        )
        assertEquals(emptyList<String>(), DestinationForm.parseAppsFilter(""))
        assertEquals(emptyList<String>(), DestinationForm.parseAppsFilter(" , , "))
    }

    @Test
    fun appsFilterFormatsBackToCommaSeparated() {
        assertEquals(
            "com.vietcombank.vcb, MB Bank",
            DestinationForm.formatAppsFilter(listOf("com.vietcombank.vcb", "MB Bank"))
        )
        assertEquals("", DestinationForm.formatAppsFilter(emptyList()))
    }
}
