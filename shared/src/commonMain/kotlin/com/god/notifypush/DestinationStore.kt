package com.god.notifypush

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

interface DestinationStore {
    suspend fun load(): List<Destination>
    suspend fun save(destinations: List<Destination>)
}

internal class JsonFileDestinationStore(private val storage: FileStorage) : DestinationStore {
    override suspend fun load(): List<Destination> {
        val raw = storage.readText(FILE_NAME) ?: return emptyList()
        return runCatching { notifyJson.decodeFromString<List<Destination>>(raw) }.getOrElse { emptyList() }
    }

    override suspend fun save(destinations: List<Destination>) {
        storage.writeText(FILE_NAME, notifyJson.encodeToString(destinations))
    }

    private companion object {
        const val FILE_NAME = "destinations.json"
    }
}

fun createDestinationStore(directoryPath: String): DestinationStore =
    JsonFileDestinationStore(createFileStorage(directoryPath))
