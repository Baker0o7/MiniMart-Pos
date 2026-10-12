package com.minimart.pos

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltAndroidApp
class MiniMartApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var settings: com.minimart.pos.data.repository.SettingsRepository
    @Inject lateinit var syncServer: com.minimart.pos.sync.SyncServer

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
        // The main device must be reachable again after a restart without anyone opening Settings.
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO).launch {
            try { if (settings.syncServerEnabled.first()) syncServer.start() } catch (_: Exception) {}
        }
    }
}
