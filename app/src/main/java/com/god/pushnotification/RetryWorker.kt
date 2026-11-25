package com.god.pushnotification

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.god.notifypush.AppSettings
import java.util.concurrent.TimeUnit

class RetryWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val force = inputData.getBoolean(RetryScheduler.KEY_FORCE, false)
        ServiceLocator.retryProcessor(applicationContext).processPending(force = force)
        val settings = ServiceLocator.settingsStore(applicationContext).load()
        if (settings.autoRetryEnabled) {
            RetryScheduler.scheduleChained(applicationContext, settings)
        }
        return Result.success()
    }
}

object RetryScheduler {

    const val CHAINED_WORK_NAME = "notify-push-retry-chained"
    const val RETRY_NOW_WORK_NAME = "notify-push-retry-now"
    const val KEY_FORCE = "force"

    fun ensureAutoRetryScheduled(context: Context, settings: AppSettings) {
        if (!settings.autoRetryEnabled) {
            cancelAutoRetry(context)
            return
        }
        WorkManager.getInstance(context).enqueueUniqueWork(
            CHAINED_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            chainedRequest(settings),
        )
    }

    fun rescheduleAutoRetry(context: Context, settings: AppSettings) {
        if (!settings.autoRetryEnabled) {
            cancelAutoRetry(context)
            return
        }
        WorkManager.getInstance(context).enqueueUniqueWork(
            CHAINED_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            chainedRequest(settings),
        )
    }

    fun scheduleChained(context: Context, settings: AppSettings) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            CHAINED_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            chainedRequest(settings),
        )
    }

    fun cancelAutoRetry(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(CHAINED_WORK_NAME)
    }

    fun retryNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<RetryWorker>()
            .setInputData(Data.Builder().putBoolean(KEY_FORCE, true).build())
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            RETRY_NOW_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    private fun chainedRequest(settings: AppSettings) =
        OneTimeWorkRequestBuilder<RetryWorker>()
            .setInitialDelay(settings.retryIntervalMinutes.toLong(), TimeUnit.MINUTES)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
            .build()
}
