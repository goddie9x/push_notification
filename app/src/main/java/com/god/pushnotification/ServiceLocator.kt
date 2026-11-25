package com.god.pushnotification

import android.content.Context
import com.god.notifypush.DestinationStore
import com.god.notifypush.Dispatcher
import com.god.notifypush.HistoryStore
import com.god.notifypush.OtpSkipFilter
import com.god.notifypush.RetryQueue
import com.god.notifypush.SettingsStore
import com.god.notifypush.createDestinationStore
import com.god.notifypush.createDispatcher
import com.god.notifypush.createHistoryStore
import com.god.notifypush.createRetryQueue
import com.god.notifypush.createSettingsStore
import java.io.File

object ServiceLocator {

    @Volatile
    private var destinationStore: DestinationStore? = null

    @Volatile
    private var settingsStore: SettingsStore? = null

    @Volatile
    private var historyStore: HistoryStore? = null

    @Volatile
    private var retryQueue: RetryQueue? = null

    @Volatile
    private var dispatcher: Dispatcher? = null

    fun destinationStore(context: Context): DestinationStore =
        destinationStore ?: synchronized(this) {
            destinationStore ?: createDestinationStore(directoryPath(context))
                .also { destinationStore = it }
        }

    fun settingsStore(context: Context): SettingsStore =
        settingsStore ?: synchronized(this) {
            settingsStore ?: createSettingsStore(directoryPath(context))
                .also { settingsStore = it }
        }

    fun historyStore(context: Context): HistoryStore =
        historyStore ?: synchronized(this) {
            historyStore ?: createHistoryStore(directoryPath(context)) {
                settingsStore(context).load()
            }.also { historyStore = it }
        }

    fun retryQueue(context: Context): RetryQueue =
        retryQueue ?: synchronized(this) {
            retryQueue ?: createRetryQueue(directoryPath(context)).also { retryQueue = it }
        }

    fun dispatcher(context: Context): Dispatcher =
        dispatcher ?: synchronized(this) {
            dispatcher ?: createDispatcher(destinationStore(context))
                .also { dispatcher = it }
        }

    fun forwarder(context: Context): NotificationForwarder =
        NotificationForwarder(
            ownPackage = context.packageName,
            dispatcher = dispatcher(context),
            historyStore = historyStore(context),
            retryQueue = retryQueue(context),
            isOtp = { payload -> OtpSkipFilter(settingsStore(context).load()).shouldSkip(payload) },
        )

    fun retryProcessor(context: Context): RetryProcessor =
        RetryProcessor(
            retryQueue(context),
            dispatcher(context),
            loadSettings = { settingsStore(context).load() },
        )

    fun testSender(): TestSender =
        TestSender(::createDispatcher)

    private fun directoryPath(context: Context): String =
        File(context.filesDir, "notifypush").absolutePath
}
