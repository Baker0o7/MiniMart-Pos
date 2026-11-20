package com.minimart.pos.cloud

import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Waits for a Google Play services task without pulling in another library. Firestore writes made while offline
 * finish only when the server answers, so every wait has a limit; the write itself stays queued by Firestore.
 */
internal suspend fun <T> Task<T>.awaitResult(timeoutMs: Long = 30_000L): T = withTimeout(timeoutMs) {
    suspendCancellableCoroutine { cont ->
        addOnSuccessListener { if (cont.isActive) cont.resume(it) }
        addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
        addOnCanceledListener { cont.cancel() }
    }
}
