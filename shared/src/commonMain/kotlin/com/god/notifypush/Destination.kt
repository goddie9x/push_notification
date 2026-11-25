package com.god.notifypush

import kotlinx.serialization.Serializable

@Serializable
data class Destination(
    val id: String,
    val name: String,
    val url: String,
    val headerName: String = "X-Api-Key",
    val headerValue: String = "",
    val enabled: Boolean = false,
    val appsFilter: List<String> = emptyList(),
) {
    fun matches(bundleId: String, appName: String): Boolean {
        if (appsFilter.isEmpty()) return true
        if (bundleId.isBlank() && appName.isBlank()) return true
        return appsFilter.any {
            it.equals(bundleId, ignoreCase = true) || it.equals(appName, ignoreCase = true)
        }
    }
}
