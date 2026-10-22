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
    @Inject lateinit var productRepo: com.minimart.pos.data.repository.ProductRepository

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        com.minimart.pos.util.CrashRecorder.install(this)
        // The main device must be reachable again after a restart without anyone opening Settings.
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO).launch {
            // Starting WorkManager opens its own database; doing that here keeps it off the launch path.
            try {
                com.minimart.pos.worker.LowStockWorker.schedule(this@MiniMartApp)
                com.minimart.pos.worker.ExpiryAlertWorker.schedule(this@MiniMartApp)
                com.minimart.pos.worker.CloudUploadWorker.schedule(this@MiniMartApp)
                com.minimart.pos.worker.AutoBackupWorker.schedule(this@MiniMartApp)
                com.minimart.pos.worker.SyncWorker.schedule(this@MiniMartApp)
                com.minimart.pos.worker.MaintenanceWorker.schedule(this@MiniMartApp)
                com.minimart.pos.worker.MpesaWebhookWorker.schedule(this@MiniMartApp)
            } catch (_: Exception) {}
            launch { settings.paperWidthMm.collect { com.minimart.pos.printer.EscPos.lineChars = com.minimart.pos.printer.EscPos.charsForPaper(it) } }
            try { productRepo.warmUp() } catch (_: Exception) {}
            try { if (settings.syncServerEnabled.first()) syncServer.start() } catch (_: Exception) {}
        }
    }
}
