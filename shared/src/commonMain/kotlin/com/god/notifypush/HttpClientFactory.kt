package com.god.notifypush

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

internal val notifyJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

internal fun <T : HttpClientEngineConfig> HttpClientConfig<T>.installNotifyJson() {
    install(ContentNegotiation) {
        json(notifyJson)
    }
}

expect fun httpClientFactory(): HttpClient
