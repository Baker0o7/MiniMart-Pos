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

            // Items already sold out can't be pulled from the shelf, so they're not worth an alert.
            val allProducts = productRepo.getExpiryCandidates().first()
            val expiring = allProducts.filter { p ->
                p.expiryDate > 0L && p.expiryDate in now..cutoff
            }
            val expired = allProducts.filter { p ->
                p.expiryDate > 0L && p.expiryDate < now
            }

            if (expiring.isNotEmpty() || expired.isNotEmpty()) {
                createChannel()

                val intent = Intent(applicationContext, MainActivity::class.java).apply {
                    // Bring the running app forward (MainActivity.onNewIntent) instead of CLEAR_TASK,
                    // which destroyed the activity and threw away an open cart.
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra(MainActivity.EXTRA_NAVIGATE_TO, "inventory")
                }
                val pending = PendingIntent.getActivity(
                    applicationContext, 1, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val title = buildString {
                    if (expired.isNotEmpty()) append(Lang.t("⛔ ${expired.size} EXPIRED", "⛔ ${expired.size} منتهية الصلاحية"))
                    if (expired.isNotEmpty() && expiring.isNotEmpty()) append(" • ")
                    if (expiring.isNotEmpty()) append(Lang.t("⚠️ ${expiring.size} expiring in ${months}mo", "⚠️ ${expiring.size} تنتهي خلال ${months} أشهر"))
                }

                val body = buildString {
                    if (expired.isNotEmpty()) {
                        append(Lang.t("Expired: ", "منتهية الصلاحية: "))
                        append(expired.take(3).joinToString(", ") { it.name })
                        if (expired.size > 3) append(Lang.t(" +${expired.size - 3} more", " +${expired.size - 3} أخرى"))
                        append("\n")
                    }
                    if (expiring.isNotEmpty()) {
                        append(Lang.t("Expiring soon: ", "تنتهي قريبًا: "))
                        append(expiring.take(3).joinToString(", ") { p ->
                            // Bug fix: (1000 * 60 * 60 * 24) is Int multiplication that
                            // silently wraps on dates far in the future (>24 days as Int
                            // overflows around 248 days). Use Long literals throughout.
                            val days = ((p.expiryDate - now) / (1000L * 60 * 60 * 24)).toInt()
                            "${p.name} (${days}${Lang.t("d", " يوم")})"
                        })
                        if (expiring.size > 3) append(Lang.t(" +${expiring.size - 3} more", " +${expiring.size - 3} أخرى"))
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
        val ch = NotificationChannel(CHANNEL_ID, Lang.t(CHANNEL_NAME, "تنبيهات الصلاحية"), NotificationManager.IMPORTANCE_HIGH).apply {
            description = Lang.t("Alerts for products nearing or past expiry date", "تنبيهات للمنتجات التي تقترب من انتهاء صلاحيتها أو انتهت")
            enableVibration(true)
        }
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(ch)
    }
}
