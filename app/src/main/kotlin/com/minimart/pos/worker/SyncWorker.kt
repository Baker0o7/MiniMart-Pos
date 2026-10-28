package com.minimart.pos.worker

import com.minimart.pos.util.RetryPolicy
import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.minimart.pos.sync.DeviceIdProvider
import com.minimart.pos.sync.SyncClient
import com.minimart.pos.sync.SyncResult
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

/**
 * Keeps a paired till in step with the main device without anyone pressing Sync Now:
 *  - a periodic run every 15 minutes, and
 *  - a short-delay run kicked after each sale or reversal (several sales in a row make one run).
 * When the main device is out of reach the work is retried with growing delays, and the sales stay
 * safely queued on the till meanwhile. No internet is needed, only the shop network, so there is
 * deliberately no network constraint: the sync itself reports "unreachable".
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val client: SyncClient,
    private val device: DeviceIdProvider,
    private val server: com.minimart.pos.sync.SyncServer,
    private val settings: com.minimart.pos.data.repository.SettingsRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // On the main device this doubles as a watchdog: if Android stopped the app, bring the server back.
        try { if (settings.syncServerEnabled.first() && !server.isRunning.value) server.start() } catch (_: Exception) {}
        val ip = device.peerIp()
        val key = device.peerKey()
        if (ip.isBlank() || key.isBlank()) return Result.success()   // not a paired till
        if (!com.minimart.pos.util.PairingCode.isStrong(key)) return Result.success()   // old short code: needs re-pairing
        return try {
            when (val r = client.sync(ip, device.id(), key)) {
                is SyncResult.Success -> Result.success()
                is SyncResult.Error ->
                    // A wrong code will not fix itself; an unreachable main device might.
                    if (r.message.contains("Pairing code", true) || r.message.contains("Too many", true) ||
                        r.message.contains("update the app", true) || r.message.contains("did not come from", true) ||
                        r.message.contains("Wi-Fi address", true)) Result.success()
                    else if (RetryPolicy.SYNC.shouldRetry(runAttemptCount)) Result.retry() else Result.success()
            }
        } catch (_: Exception) {
            if (RetryPolicy.SYNC.shouldRetry(runAttemptCount)) Result.retry() else Result.success()
        }
    }

    companion object {
        private const val PERIODIC = "sync_periodic"
        private const val KICK = "sync_kick"

        fun schedule(context: Context) {
            val wm = WorkManager.getInstance(context)
            wm.enqueueUniquePeriodicWork(
                PERIODIC, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES).build()
            )
        }

        /** Ask for a sync soon. Calls within the delay collapse into one run. */
        fun kick(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setInitialDelay(15, TimeUnit.SECONDS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(KICK, ExistingWorkPolicy.KEEP, request)
        }
    }
}
