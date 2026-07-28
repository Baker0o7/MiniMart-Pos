package com.minimart.pos.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Offline-only — no network sync (this predates the real LAN sync feature in
 * sync/SyncServer.kt + sync/SyncClient.kt). Stub kept only so any historical
 * WorkManager registration doesn't break; not currently scheduled anywhere.
 *
 * Bug fix: this docstring used to claim "all data lives in the local encrypted
 * SQLCipher database" — SQLCipher was attempted twice in this project and
 * reverted both times due to native-library crashes on app startup. The
 * database is currently NOT app-level encrypted; it relies on Android's
 * File-Based Encryption (FBE), which is the OS-level default since Android 7.0
 * and covers all devices this app supports (minSdk 26). Leaving a false
 * encryption claim in the codebase is worse than no claim at all — a future
 * audit or developer could rely on it.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = Result.success()
}
