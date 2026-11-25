package com.god.notifypush

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class FakeDestinationStore(private val destinations: List<Destination>) : DestinationStore {
    override suspend fun load(): List<Destination> = destinations
    override suspend fun save(destinations: List<Destination>) = Unit
}

internal class InMemoryFileStorage : FileStorage {
    private val files = mutableMapOf<String, String>()
    override fun readText(fileName: String): String? = files[fileName]
    override fun writeText(fileName: String, text: String) {
        files[fileName] = text
    }
}

class DispatcherTest {
    private val payload = NotificationPayload.fromNotification(
        sourceApp = "Vietcombank",
        bundleId = "com.vietcombank.vcb",
        title = "Vietcombank",
        subtitle = "",
        body = "+500.000 VND",
        device = "Pixel 8",
    )

    private fun destination(id: String, url: String, enabled: Boolean = true): Destination =
        Destination(id = id, name = id, url = url, enabled = enabled)

    private fun clientFor(responses: Map<String, HttpStatusCode>, failing: Set<String> = emptySet()): HttpClient {
        val engine = MockEngine { request ->
            val url = request.url.toString()
            if (url in failing) throw IOException("connection refused")
            respond("{}", responses[url] ?: HttpStatusCode.NotFound)
        }
        return HttpClient(engine) {
            install(ContentNegotiation) { json() }
        }
    }

    @Test
    fun allDestinationsDelivered() = runTest {
        val client = clientFor(
            mapOf(
                "https://a.example/ingest" to HttpStatusCode.Created,
                "https://b.example/ingest" to HttpStatusCode.OK,
            ),
        )
        val dispatcher = Dispatcher(
            client,
            FakeDestinationStore(
                listOf(
                    destination("a", "https://a.example/ingest"),
                    destination("b", "https://b.example/ingest"),
                ),
            ),
        )
        val results = dispatcher.dispatch(payload)
        assertEquals(2, results.size)
        assertTrue(results.all { it.status == DispatchStatus.DELIVERED })
        assertEquals(201, results.first { it.destinationId == "a" }.httpCode)
    }

    @Test
    fun oneFailureDoesNotBlockOthers() = runTest {
        val client = clientFor(
            mapOf(
                "https://a.example/ingest" to HttpStatusCode.OK,
                "https://b.example/ingest" to HttpStatusCode.InternalServerError,
                "https://c.example/ingest" to HttpStatusCode.OK,
            ),
        )
        val dispatcher = Dispatcher(
            client,
            FakeDestinationStore(
                listOf(
                    destination("a", "https://a.example/ingest"),
                    destination("b", "https://b.example/ingest"),
                    destination("c", "https://c.example/ingest"),
                ),
            ),
        )
        val results = dispatcher.dispatch(payload)
        assertEquals(DispatchStatus.DELIVERED, results.first { it.destinationId == "a" }.status)
        assertEquals(DispatchStatus.REJECTED, results.first { it.destinationId == "b" }.status)
        assertEquals(500, results.first { it.destinationId == "b" }.httpCode)
        assertEquals(DispatchStatus.DELIVERED, results.first { it.destinationId == "c" }.status)
    }

    @Test
    fun status422CountsAsDelivered() = runTest {
        val client = clientFor(mapOf("https://a.example/ingest" to HttpStatusCode.UnprocessableEntity))
        val dispatcher = Dispatcher(
            client,
            FakeDestinationStore(listOf(destination("a", "https://a.example/ingest"))),
        )
        val results = dispatcher.dispatch(payload)
        assertEquals(DispatchStatus.DELIVERED, results.single().status)
        assertEquals(422, results.single().httpCode)
    }

    @Test
    fun transportFailureIsFailedWithMessage() = runTest {
        val client = clientFor(emptyMap(), failing = setOf("https://a.example/ingest"))
        val dispatcher = Dispatcher(
            client,
            FakeDestinationStore(listOf(destination("a", "https://a.example/ingest"))),
        )
        val results = dispatcher.dispatch(payload)
        assertEquals(DispatchStatus.FAILED, results.single().status)
        assertEquals("connection refused", results.single().error)
        assertNull(results.single().httpCode)
    }

    @Test
    fun disabledDestinationIsSkipped() = runTest {
        val client = clientFor(mapOf("https://a.example/ingest" to HttpStatusCode.OK))
        val dispatcher = Dispatcher(
            client,
            FakeDestinationStore(
                listOf(
                    destination("a", "https://a.example/ingest", enabled = false),
                ),
            ),
        )
        assertTrue(dispatcher.dispatch(payload).isEmpty())
    }

    @Test
    fun nonMatchingAppFilterIsSkipped() = runTest {
        val client = clientFor(mapOf("https://a.example/ingest" to HttpStatusCode.OK))
        val filtered = Destination(
            id = "a",
            name = "a",
            url = "https://a.example/ingest",
            appsFilter = listOf("com.other.app"),
        )
        val dispatcher = Dispatcher(client, FakeDestinationStore(listOf(filtered)))
        assertTrue(dispatcher.dispatch(payload).isEmpty())
    }
}
