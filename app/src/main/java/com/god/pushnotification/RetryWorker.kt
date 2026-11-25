package com.god.pushnotification

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class RetryWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val outcome = ServiceLocator.retryProcessor(applicationContext).processPending()
        return if (outcome.hasFailures) Result.retry() else Result.success()
    }
}

object RetryScheduler {

    const val PERIODIC_WORK_NAME = "notify-push-retry-periodic"
    const val RETRY_NOW_WORK_NAME = "notify-push-retry-now"

    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<RetryWorker>(1, TimeUnit.HOURS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun retryNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<RetryWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            RETRY_NOW_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
