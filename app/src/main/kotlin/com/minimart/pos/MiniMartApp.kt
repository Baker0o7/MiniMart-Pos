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
        // Pre-load SQLCipher native libs (.so) before any database operation.
        // Without this, the first DB access on a cold start can race with lib loading
        // and cause UnsatisfiedLinkError on some devices (the root cause of the
        // previous failed SQLCipher attempt in this project).
        net.sqlcipher.database.SQLiteDatabase.loadLibs(this)
        // Schedule low-stock check — wrapped in try/catch so a WorkManager
        // issue never prevents the app from starting
        try {
            com.minimart.pos.worker.LowStockWorker.schedule(this)
            com.minimart.pos.worker.ExpiryAlertWorker.schedule(this)
        } catch (_: Exception) {}
    }
}
