package com.minimart.pos

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MiniMartApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        try {
            com.minimart.pos.worker.LowStockWorker.schedule(this)
            com.minimart.pos.worker.ExpiryAlertWorker.schedule(this)
            com.minimart.pos.worker.CloudUploadWorker.schedule(this)
            com.minimart.pos.worker.AutoBackupWorker.schedule(this)
            com.minimart.pos.worker.SyncWorker.schedule(this)
        } catch (_: Exception) {}
    }
}
