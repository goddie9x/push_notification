package com.god.notifypush

import kotlinx.serialization.Serializable

@Serializable
data class Destination(
    val id: String,
    val name: String,
    val url: String,
    val headerName: String = "X-Api-Key",
    val headerValue: String = "",
    val enabled: Boolean = true,
    val appsFilter: List<String> = emptyList(),
) {
    fun matches(bundleId: String, appName: String): Boolean {
        if (appsFilter.isEmpty()) return true
        return appsFilter.any { it == bundleId || it == appName }
    }
}
