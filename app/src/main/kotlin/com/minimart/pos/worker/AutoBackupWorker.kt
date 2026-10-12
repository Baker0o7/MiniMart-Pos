package com.minimart.pos.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.minimart.pos.util.AuditEvent
import com.minimart.pos.util.AuditLogger
import com.minimart.pos.util.BackupManager
import com.minimart.pos.util.BackupResult
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/** Takes a checked backup of the shop database about once a day and keeps the newest few. */
@HiltWorker
class AutoBackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val audit: AuditLogger
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result =
        when (val r = BackupManager.autoBackup(applicationContext)) {
            is BackupResult.Success -> { runCatching { audit.log(AuditEvent.BACKUP_CREATED, user = "system", detail = r.file.name) }; Result.success() }
            is BackupResult.Error -> {
                runCatching { audit.log(AuditEvent.BACKUP_FAILED, user = "system", detail = r.message) }
                if (runAttemptCount < 2) Result.retry() else Result.success()   // tomorrow's run tries again
            }
        }

    companion object {
        const val WORK_NAME = "auto_backup"
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(24, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiresStorageNotLow(true).build())
                .setInitialDelay(10, TimeUnit.MINUTES)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
