package com.minimart.pos.worker

import com.minimart.pos.util.RetryPolicy
import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.minimart.pos.data.repository.DatabaseArchiver
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/** Weekly housekeeping while the phone is idle and charging: drops old delivered sync records and refreshes query statistics. */
@HiltWorker
class MaintenanceWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val archiver: DatabaseArchiver
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        archiver.routineMaintenance()
        Result.success()
    } catch (_: Exception) {
        if (RetryPolicy.QUICK.shouldRetry(runAttemptCount)) Result.retry() else Result.success()
    }

    companion object {
        const val WORK_NAME = "maintenance"
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<MaintenanceWorker>(7, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiresCharging(true).setRequiresDeviceIdle(true).build())
                .setInitialDelay(1, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
