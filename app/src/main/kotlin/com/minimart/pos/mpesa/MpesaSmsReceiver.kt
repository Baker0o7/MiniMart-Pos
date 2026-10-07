package com.minimart.pos.mpesa

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.minimart.pos.data.entity.MpesaPayment
import com.minimart.pos.data.repository.MpesaPaymentRepository
import com.minimart.pos.data.repository.SettingsRepository
import com.minimart.pos.util.MpesaSmsParser
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Logs incoming M-Pesa payments as they arrive. Only texts whose sender ID is "MPESA" are read:
 * a message typed or forwarded from an ordinary phone number is ignored, so it can never create a
 * "verified" payment. The transaction code is unique, so a repeated text is stored once.
 */
@AndroidEntryPoint
class MpesaSmsReceiver : BroadcastReceiver() {
    @Inject lateinit var repo: MpesaPaymentRepository
    @Inject lateinit var settings: SettingsRepository

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        // A long message arrives as several parts from the same sender; join them.
        val messages = parts.groupBy { it.originatingAddress.orEmpty() }
            .filterKeys { MpesaSmsParser.isTrustedSender(it) }
            .map { (_, p) -> p.joinToString("") { it.messageBody.orEmpty() } to p.first().timestampMillis }
        if (messages.isEmpty()) return

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (!settings.mpesaSmsTracking.first()) return@launch
                for ((body, sentAt) in messages) {
                    val parsed = MpesaSmsParser.parse(body) ?: continue
                    repo.record(
                        MpesaPayment(
                            code = parsed.code,
                            amount = parsed.amount,
                            senderName = parsed.senderName,
                            senderPhone = parsed.senderPhone,
                            receivedAt = parsed.timestamp ?: sentAt,
                            rawMessage = body
                        )
                    )
                }
            } catch (e: Exception) {
                Log.w("MpesaSmsReceiver", "Could not record M-Pesa payment", e)
            } finally {
                pending.finish()
            }
        }
    }
}
