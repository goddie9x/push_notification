package com.god.notifypush

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin

actual fun httpClientFactory(): HttpClient = HttpClient(Darwin) {
    installNotifyJson()
}
