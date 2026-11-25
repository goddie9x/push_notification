package com.god.pushnotification

object DestinationForm {

    fun parseAppsFilter(raw: String): List<String> =
        raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    fun formatAppsFilter(appsFilter: List<String>): String =
        appsFilter.joinToString(", ")

    fun urlError(url: String): String? =
        when {
            url.isBlank() -> "URL is required"
            !url.startsWith("http://") && !url.startsWith("https://") ->
                "URL must start with http:// or https://"
            else -> null
        }
}
