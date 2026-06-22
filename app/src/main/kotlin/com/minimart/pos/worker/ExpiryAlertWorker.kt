package com.minimart.pos.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.minimart.pos.MainActivity
import com.minimart.pos.data.repository.ProductRepository
import com.minimart.pos.data.repository.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

@HiltWorker
class ExpiryAlertWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val productRepo: ProductRepository,
    private val settingsRepo: SettingsRepository
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME       = "expiry_check"
        const val CHANNEL_ID      = "expiry_alerts"
        const val CHANNEL_NAME    = "Expiry Alerts"
        const val NOTIFICATION_ID = 1002

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ExpiryAlertWorker>(
                repeatInterval = 12, repeatIntervalTimeUnit = TimeUnit.HOURS
            )
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .setInitialDelay(2, TimeUnit.MINUTES)
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
            val months = settingsRepo.expiryAlertMonths.first()
            val thresholdMs = months * 30L * 24 * 60 * 60 * 1000
            val now = System.currentTimeMillis()
            val cutoff = now + thresholdMs

            val allProducts = productRepo.getAllProducts().first()
            val expiring = allProducts.filter { p ->
                p.expiryDate > 0L && p.expiryDate in now..cutoff
            }
            val expired = allProducts.filter { p ->
                p.expiryDate > 0L && p.expiryDate < now
            }

            if (expiring.isNotEmpty() || expired.isNotEmpty()) {
                createChannel()

                val intent = Intent(applicationContext, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    putExtra("navigate_to", "inventory")
                }
                val pending = PendingIntent.getActivity(
                    applicationContext, 1, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val title = buildString {
                    if (expired.isNotEmpty()) append("⛔ ${expired.size} EXPIRED")
                    if (expired.isNotEmpty() && expiring.isNotEmpty()) append(" • ")
                    if (expiring.isNotEmpty()) append("⚠️ ${expiring.size} expiring in ${months}mo")
                }

                val body = buildString {
                    if (expired.isNotEmpty()) {
                        append("Expired: ")
                        append(expired.take(3).joinToString(", ") { it.name })
                        if (expired.size > 3) append(" +${expired.size - 3} more")
                        append("\n")
                    }
                    if (expiring.isNotEmpty()) {
                        append("Expiring soon: ")
                        append(expiring.take(3).joinToString(", ") { p ->
                            // Bug fix: (1000 * 60 * 60 * 24) is Int multiplication that
                            // silently wraps on dates far in the future (>24 days as Int
                            // overflows around 248 days). Use Long literals throughout.
                            val days = ((p.expiryDate - now) / (1000L * 60 * 60 * 24)).toInt()
                            "${p.name} (${days}d)"
                        })
                        if (expiring.size > 3) append(" +${expiring.size - 3} more")
                    }
                }

                val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_alert)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setContentIntent(pending)
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

    private fun createChannel() {
        val ch = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Alerts for products nearing or past expiry date"
            enableVibration(true)
        }
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(ch)
    }
}
