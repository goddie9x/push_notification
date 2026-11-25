package com.god.notifypush

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

actual fun httpClientFactory(): HttpClient = HttpClient(OkHttp) {
    installNotifyJson()
}
