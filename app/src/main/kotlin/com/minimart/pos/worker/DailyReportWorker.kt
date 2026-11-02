package com.minimart.pos.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.minimart.pos.MainActivity
import com.minimart.pos.data.dao.SyncDao
import com.minimart.pos.data.entity.PaymentMethod
import com.minimart.pos.data.entity.SaleStatus
import com.minimart.pos.data.repository.ExpenseRepository
import com.minimart.pos.data.repository.MpesaPaymentRepository
import com.minimart.pos.data.repository.ProductRepository
import com.minimart.pos.data.repository.SaleRepository
import com.minimart.pos.data.repository.SettingsRepository
import com.minimart.pos.ui.viewmodel.reconcileRange
import com.minimart.pos.util.DailySummary
import com.minimart.pos.util.DailySummaryInput
import com.minimart.pos.util.Money
import com.minimart.pos.util.sum
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Posts the owner's end-of-day summary at the hour they chose, then schedules tomorrow's.
 * The text comes from [DailySummary] (tested); this class only gathers the day's numbers.
 * It never sends anything by itself: the notification has buttons that open SMS or the share sheet.
 */
@HiltWorker
class DailyReportWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val sales: SaleRepository,
    private val expenses: ExpenseRepository,
    private val products: ProductRepository,
    private val mpesa: MpesaPaymentRepository,
    private val settings: SettingsRepository,
    private val syncDao: SyncDao
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "daily_report"
        const val CHANNEL_ID = "daily_report"
        const val NOTIFICATION_ID = 1003
        private const val KEY_TEST = "test"

        /** [replace] = true when the owner changed the time; false at app start so a pending run is kept. */
        fun schedule(context: Context, hour: Int, replace: Boolean) {
            val delay = DailySummary.delayUntilHour(System.currentTimeMillis(), hour)
            val request = OneTimeWorkRequestBuilder<DailyReportWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS).build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME, if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) { WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME) }

        /** Sends today's report right now (for the "Send a test" button). Does not change the schedule. */
        fun runNow(context: Context) {
            WorkManager.getInstance(context).enqueue(
                OneTimeWorkRequestBuilder<DailyReportWorker>().setInputData(Data.Builder().putBoolean(KEY_TEST, true).build()).build())
        }
    }

    override suspend fun doWork(): Result {
        val test = inputData.getBoolean(KEY_TEST, false)
        if (!test && !settings.dailyReport.first()) return Result.success()   // switched off: stop the daily chain
        val hour = settings.dailyReportHour.first()
        val result = try {
            post(buildInput(hour, test))
            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (runAttemptCount < 3) return Result.retry()
            Result.failure()
        }
        // Tomorrow's report is queued whether or not today's worked. APPEND so this running job is not cancelled by it.
        if (!test) reschedule(hour)
        return result
    }

    private fun reschedule(hour: Int) {
        val delay = DailySummary.delayUntilHour(System.currentTimeMillis() + 60_000L, hour)
        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<DailyReportWorker>().setInitialDelay(delay, TimeUnit.MILLISECONDS).build())
    }

    private suspend fun buildInput(hour: Int, test: Boolean): DailySummaryInput {
        // A report set for the small hours is about the day that just ended, not the one that just began.
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (!test && hour < 4) add(Calendar.DAY_OF_MONTH, -1)
        }
        val start = cal.timeInMillis
        val dateLabel = SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(start))
        cal.add(Calendar.DAY_OF_MONTH, 1)
        val end = cal.timeInMillis

        val all = sales.getSalesByDateRange(start, end).first()
        val done = all.filter { it.status == SaleStatus.COMPLETED }
        fun total(method: PaymentMethod) = done.filter { it.paymentMethod == method }.map { Money.fromDouble(it.totalAmount) }.sum()
        val cash = done.filter { it.paymentMethod == PaymentMethod.CASH }.map { Money.fromDouble(it.totalAmount) }.sum() +
            done.filter { it.paymentMethod == PaymentMethod.MIXED }.map { Money.fromDouble(it.cashPortion) }.sum()
        val profit = runCatching { sales.profitBetween(start, end) }.getOrDefault(0.0).takeIf { it != 0.0 }

        return DailySummaryInput(
            storeName = settings.storeName.first(),
            currency = settings.currency.first(),
            dateLabel = dateLabel,
            salesCount = done.size,
            revenue = done.map { Money.fromDouble(it.totalAmount) }.sum(),
            cash = cash,
            mpesa = total(PaymentMethod.MPESA),
            card = total(PaymentMethod.CARD),
            onCredit = total(PaymentMethod.CREDIT),
            refundsCount = all.count { it.status == SaleStatus.REFUNDED },
            voidsCount = all.count { it.status == SaleStatus.VOIDED },
            expenses = Money.fromDouble(runCatching { expenses.getTotalExpenses(start, end).first() }.getOrNull() ?: 0.0),
            profit = profit?.let { Money.fromDouble(it) },
            topProducts = runCatching { sales.getTopSellers(start, end).first() }.getOrDefault(emptyList())
                .take(3).map { it.productName to it.totalQty },
            lowStockCount = runCatching { products.getLowStockProducts().first().size }.getOrDefault(0),
            unclaimedMpesaCount = runCatching { reconcileRange(mpesa, sales, start, end).unclaimedPayments.size }.getOrDefault(0),
            pendingSync = runCatching { syncDao.countPending() }.getOrDefault(0)
        )
    }

    private suspend fun post(input: DailySummaryInput) {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Daily report", NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = "The owner's end-of-day business summary" })
        val body = DailySummary.body(input)
        val open = PendingIntent.getActivity(applicationContext, 3,
            Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(MainActivity.EXTRA_NAVIGATE_TO, "reports")
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val share = PendingIntent.getActivity(applicationContext, 4,
            Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, body), "Send report"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(DailySummary.title(input))
            .setContentText(body.lineSequence().drop(1).firstOrNull().orEmpty())
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open).setAutoCancel(true)
            .addAction(0, "Share", share)
        val phone = settings.ownerReportPhone.first()
        if (phone.isNotBlank()) {
            val sms = PendingIntent.getActivity(applicationContext, 5,
                Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phone")).putExtra("sms_body", body),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(0, "SMS owner", sms)
        }
        nm.notify(NOTIFICATION_ID, builder.build())
    }
}
