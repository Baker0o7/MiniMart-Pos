package com.minimart.pos.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.minimart.pos.cloud.MpesaWebhookClient
import com.minimart.pos.cloud.WebhookResult
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/** Every 15 minutes (the shortest Android allows) fetches payments Safaricom has pushed to the cloud inbox. */
@HiltWorker
class MpesaWebhookWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val client: MpesaWebhookClient
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!client.enabled) return Result.success()
        return try {
            when (client.pull()) {
                is WebhookResult.Failed -> if (runAttemptCount < 2) Result.retry() else Result.success()
                else -> Result.success()
            }
        } catch (_: Exception) { if (runAttemptCount < 2) Result.retry() else Result.success() }
    }

    companion object {
        const val WORK_NAME = "mpesa_webhook_pull"
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<MpesaWebhookWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
        fun cancel(context: Context) { WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME) }
    }
}
