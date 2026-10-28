package com.minimart.pos.worker

import com.minimart.pos.util.RetryPolicy
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
    private val audit: AuditLogger,
    private val integrity: com.minimart.pos.data.repository.IntegrityChecker
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // A nightly look at the books: anything that no longer adds up goes in the activity log.
        runCatching {
            val report = integrity.check()
            if (!report.ok) audit.log(AuditEvent.DATA_CHECK, user = "system",
                detail = "${report.issues.size} problem(s): ${report.issues.take(3).joinToString("; ")}")
        }
        return runBackup()
    }

    private suspend fun runBackup(): Result =
        when (val r = BackupManager.autoBackup(applicationContext)) {
            is BackupResult.Success -> { runCatching { audit.log(AuditEvent.BACKUP_CREATED, user = "system", detail = r.file.name) }; Result.success() }
            is BackupResult.Error -> {
                runCatching { audit.log(AuditEvent.BACKUP_FAILED, user = "system", detail = r.message) }
                if (RetryPolicy.QUICK.shouldRetry(runAttemptCount)) Result.retry() else Result.success()   // tomorrow's run tries again
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
