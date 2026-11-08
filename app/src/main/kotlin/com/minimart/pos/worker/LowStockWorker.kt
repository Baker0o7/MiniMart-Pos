package com.minimart.pos.worker

import com.minimart.pos.util.Lang
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.minimart.pos.MainActivity
import com.minimart.pos.R
import com.minimart.pos.data.repository.ProductRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

@HiltWorker
class LowStockWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val productRepo: ProductRepository
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME       = "low_stock_check"
        const val CHANNEL_ID      = "low_stock_alerts"
        const val CHANNEL_NAME    = "Low Stock Alerts"
        const val NOTIFICATION_ID = 1001

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<LowStockWorker>(
                repeatInterval = 4, repeatIntervalTimeUnit = TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .setInitialDelay(5, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }

    override suspend fun doWork(): Result {
        return try {
            val lowStockProducts = productRepo.getLowStockProducts().first()
            if (lowStockProducts.isNotEmpty()) {
                createNotificationChannel()
                val names = lowStockProducts.take(5).joinToString(", ") { "${it.name} (${it.stockLabel})" }
                val more = if (lowStockProducts.size > 5) Lang.t(" +${lowStockProducts.size - 5} more", " +${lowStockProducts.size - 5} أخرى") else ""

                val intent = Intent(applicationContext, MainActivity::class.java).apply {
                    // Bring the running app forward (MainActivity.onNewIntent) instead of CLEAR_TASK,
                    // which destroyed the activity and threw away an open cart.
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra(MainActivity.EXTRA_NAVIGATE_TO, "inventory")
                }
                val pendingIntent = PendingIntent.getActivity(
                    applicationContext, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_alert)
                    .setContentTitle(Lang.t("⚠️ Low Stock Alert — ${lowStockProducts.size} items", "⚠️ تنبيه مخزون منخفض — ${lowStockProducts.size} منتجات"))
                    .setContentText("$names$more")
                    .setStyle(NotificationCompat.BigTextStyle().bigText(Lang.t("Low on stock:", "مخزون منخفض:") + "\n$names$more"))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .build()

                val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(NOTIFICATION_ID, notification)
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, Lang.t(CHANNEL_NAME, "تنبيهات المخزون المنخفض"), NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = Lang.t("Alerts when products are running low on stock", "تنبيهات عند اقتراب نفاد المنتجات")
            enableVibration(true)
        }
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }
}
