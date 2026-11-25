package com.god.notifypush

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO

actual fun httpClientFactory(): HttpClient = HttpClient(CIO) {
    installNotifyJson()
}
