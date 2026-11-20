package com.minimart.pos.cloud

import com.google.firebase.firestore.FirebaseFirestore
import com.minimart.pos.data.repository.SettingsRepository
import com.minimart.pos.util.ErrorAdvice
import com.minimart.pos.util.Lang
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

sealed class CloudResult {
    object Ok : CloudResult()
    data class Failed(val message: String) : CloudResult()
}

/** Uploads this shop's summary to the signed-in owner's own area of Firestore (`owners/<uid>/stores/<shop>`). */
@Singleton
class CloudReporter @Inject constructor(
    private val config: CloudConfig,
    private val auth: OwnerAuth,
    private val builder: CloudSnapshotBuilder,
    private val settings: SettingsRepository
) {
    private val lock = Mutex()   // one upload at a time (worker, shift close and the button can overlap)

    suspend fun push(): CloudResult = lock.withLock {
        val uid = auth.uid ?: return CloudResult.Failed(Lang.t("Sign in with Google first.", "سجّل الدخول عبر Google أولاً."))
        withContext(Dispatchers.IO) {
            try {
                val name = try { settings.storeName.first() } catch (_: Exception) { "Store" }
                val payload = builder.build().toString()
                if (payload.length > MAX_PAYLOAD) return@withContext CloudResult.Failed(Lang.t("The summary is too large to upload.", "الملخص كبير جدًا للرفع."))
                FirebaseFirestore.getInstance().collection("owners").document(uid).collection("stores").document(config.storeId)
                    .set(mapOf("name" to name.take(80), "pushedAt" to System.currentTimeMillis(), "payload" to payload)).awaitResult()
                config.lastPushAt = System.currentTimeMillis()
                CloudResult.Ok
            } catch (_: TimeoutCancellationException) {
                CloudResult.Failed(Lang.t("No connection right now. The upload will go through when the internet is back.",
                    "لا يوجد اتصال الآن. سيتم الرفع عند عودة الإنترنت."))
            } catch (e: Exception) {
                CloudResult.Failed(ErrorAdvice.explain(e, "The upload"))
            }
        }
    }

    /** Removes everything this owner has in the cloud (all shops and the shared product list). Needs the internet. */
    suspend fun deleteCloudData(): CloudResult = lock.withLock {
        val uid = auth.uid ?: return CloudResult.Ok
        withContext(Dispatchers.IO) {
            try {
                val owner = FirebaseFirestore.getInstance().collection("owners").document(uid)
                owner.collection("stores").get().awaitResult().documents.forEach { it.reference.delete().awaitResult() }
                owner.collection("catalog").document("main").delete().awaitResult()
                CloudResult.Ok
            } catch (e: Exception) {
                CloudResult.Failed(ErrorAdvice.explain(e, "Deleting the cloud copy"))
            }
        }
    }

    private companion object { const val MAX_PAYLOAD = 900_000 }   // a Firestore document holds about 1 MB
}
