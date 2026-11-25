package com.god.pushnotification

import android.app.Notification
import android.os.Bundle
import com.god.notifypush.NotificationPayload

object PayloadExtractor {

    fun fromNotification(
        packageName: String,
        appName: String,
        title: CharSequence?,
        subtitle: CharSequence?,
        body: CharSequence?
    ): NotificationPayload =
        NotificationPayload.fromNotification(
            sourceApp = appName,
            bundleId = packageName,
            title = title?.toString().orEmpty(),
            subtitle = subtitle?.toString().orEmpty(),
            body = body?.toString().orEmpty(),
            device = "android"
        )

    fun fromExtras(packageName: String, appName: String, extras: Bundle?): NotificationPayload {
        val bigText = extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)
        val text = extras?.getCharSequence(Notification.EXTRA_TEXT)
        return fromNotification(
            packageName = packageName,
            appName = appName,
            title = extras?.getCharSequence(Notification.EXTRA_TITLE),
            subtitle = extras?.getCharSequence(Notification.EXTRA_SUB_TEXT),
            body = bigText ?: text
        )
    }
}
