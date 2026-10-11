package com.minimart.pos.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.minimart.pos.cloud.CloudConfig
import com.minimart.pos.cloud.CloudReporter
import com.minimart.pos.cloud.CloudResult
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/** Periodically sends the store summary to the owner's cloud dashboard (only if connected). */
@HiltWorker
class CloudUploadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val config: CloudConfig,
    private val reporter: CloudReporter
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!config.isConfigured) return Result.success()
        return try {
            when (reporter.push()) {
                CloudResult.Ok -> Result.success()
                is CloudResult.Failed -> if (runAttemptCount < 3) Result.retry() else Result.success()
            }
        } catch (_: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.success()
        }
    }

    companion object {
        const val WORK_NAME = "cloud_upload"
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<CloudUploadWorker>(3, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInitialDelay(5, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
