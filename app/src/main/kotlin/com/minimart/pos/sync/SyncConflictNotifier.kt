package com.minimart.pos.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.minimart.pos.MainActivity
import com.minimart.pos.util.ConflictAlert
import com.minimart.pos.util.Lang
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tells the owner right away when two tills disagreed about a sale, instead of leaving it only in the activity log.
 * Best effort: if notifications are off or posting fails, the audit entry written by [SyncApplier] is still there.
 */
@Singleton
class SyncConflictNotifier @Inject constructor(@ApplicationContext private val context: Context) {

    fun reversalConflict(receipt: String, kept: String, other: String) {
        try {
            val nm = NotificationManagerCompat.from(context)
            if (!nm.areNotificationsEnabled()) return
            val text = ConflictAlert.reversal(receipt, kept, other)
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, Lang.t("Sync conflicts", "تعارضات المزامنة"), NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = Lang.t("When two tills disagree about a sale", "عندما يختلف صندوقان على عملية بيع") }
            )
            val open = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val n = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(text.title)
                .setContentText(text.body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text.body))
                .setContentIntent(open)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
            // One notification per sale: the same conflict arriving twice replaces itself.
            nm.notify(receipt.hashCode(), n)
        } catch (e: SecurityException) {
            Log.w("SyncConflict", "No permission to post the conflict alert", e)
        } catch (e: Exception) {
            Log.w("SyncConflict", "Could not post the conflict alert", e)
        }
    }

    private companion object { const val CHANNEL_ID = "sync_conflicts" }
}
