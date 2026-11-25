package com.god.pushnotification

import com.god.notifypush.Destination
import com.god.notifypush.DestinationStore
import com.god.notifypush.DispatchResult
import com.god.notifypush.DispatchStatus
import com.god.notifypush.Dispatcher
import com.god.notifypush.TestPayloadBuilder

class TestSender(private val createDispatcher: (DestinationStore) -> Dispatcher) {

    suspend fun sendTest(destination: Destination): DispatchResult {
        val results = createDispatcher(SingleDestinationStore(destination.copy(enabled = true)))
            .dispatch(TestPayloadBuilder.sample("android"))
        return results.firstOrNull()
            ?: DispatchResult(
                destination.id,
                destination.name,
                DispatchStatus.FAILED,
                error = "Destination filter excludes the test payload"
            )
    }

    private class SingleDestinationStore(
        private val destination: Destination
    ) : DestinationStore {
        override suspend fun load(): List<Destination> = listOf(destination)
        override suspend fun save(destinations: List<Destination>) {}
    }
}
