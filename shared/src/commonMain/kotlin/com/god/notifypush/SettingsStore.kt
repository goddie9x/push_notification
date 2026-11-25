package com.god.notifypush

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

interface SettingsStore {
    suspend fun load(): AppSettings
    suspend fun save(settings: AppSettings)
}

internal class JsonFileSettingsStore(private val storage: FileStorage) : SettingsStore {
    override suspend fun load(): AppSettings {
        val raw = storage.readText(FILE_NAME) ?: return AppSettings()
        return runCatching { notifyJson.decodeFromString<AppSettings>(raw) }
            .getOrElse { AppSettings() }
            .normalized()
    }

    override suspend fun save(settings: AppSettings) {
        storage.writeText(FILE_NAME, notifyJson.encodeToString(settings.normalized()))
    }

    private companion object {
        const val FILE_NAME = "settings.json"
    }
}

fun createSettingsStore(directoryPath: String): SettingsStore =
    JsonFileSettingsStore(createFileStorage(directoryPath))
