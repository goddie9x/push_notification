package com.god.notifypush

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class Dispatcher(
    private val httpClient: HttpClient,
    private val store: DestinationStore,
) {
    suspend fun dispatch(payload: NotificationPayload): List<DispatchResult> {
        val destinations = store.load()
            .filter { it.enabled && it.matches(payload.bundleId, payload.sourceApp) }
        return coroutineScope {
            destinations.map { destination ->
                async { postTo(destination, payload) }
            }.awaitAll()
        }
    }

    private suspend fun postTo(destination: Destination, payload: NotificationPayload): DispatchResult {
        return try {
            val response = httpClient.post(destination.url) {
                contentType(ContentType.Application.Json)
                if (destination.headerValue.isNotEmpty()) {
                    header(destination.headerName, destination.headerValue)
                }
                setBody(payload)
            }
            val code = response.status.value
            if (response.status.isSuccess() || code == 422) {
                DispatchResult(destination.id, destination.name, DispatchStatus.DELIVERED, httpCode = code)
            } else {
                DispatchResult(destination.id, destination.name, DispatchStatus.REJECTED, httpCode = code)
            }
        } catch (e: Exception) {
            DispatchResult(destination.id, destination.name, DispatchStatus.FAILED, error = e.message)
        }
    }
}
