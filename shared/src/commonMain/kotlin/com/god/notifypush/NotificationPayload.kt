package com.god.notifypush

import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable

@Serializable
data class NotificationPayload(
    val schema: String = "notify-push/v1",
    val sourceApp: String,
    val bundleId: String,
    val title: String,
    val subtitle: String,
    val body: String,
    val receivedAt: String,
    val rawText: String,
    val device: String,
) {
    companion object {
        fun fromNotification(
            sourceApp: String,
            bundleId: String,
            title: String,
            subtitle: String,
            body: String,
            device: String,
        ): NotificationPayload {
            val rawText = listOf(title, subtitle, body)
                .filter { it.isNotEmpty() }
                .joinToString("\n")
            return NotificationPayload(
                sourceApp = sourceApp,
                bundleId = bundleId,
                title = title,
                subtitle = subtitle,
                body = body,
                receivedAt = Clock.System.now().toString(),
                rawText = rawText,
                device = device,
            )
        }
    }
}
