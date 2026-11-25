package com.god.notifypush

object TestPayloadBuilder {
    fun sample(device: String): NotificationPayload =
        NotificationPayload.fromNotification(
            sourceApp = "NotifyPush",
            bundleId = "com.god.pushnotification",
            title = "Test notification",
            subtitle = "",
            body = "This is a test notification from NotifyPush",
            device = device,
        )
}
