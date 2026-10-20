package com.minimart.pos.sync

import android.util.Log
import com.minimart.pos.util.SyncCrypto
import com.minimart.pos.data.dao.SyncDao
import com.minimart.pos.data.entity.SyncStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import com.minimart.pos.util.LanHttp
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

sealed class SyncResult {
    /** [pushed] sales the main device accepted, [rejected] it refused, then what changed locally. */
    data class Success(val pushed: Int, val rejected: Int, val products: Int, val customers: Int, val rejectedReasons: List<String> = emptyList()) : SyncResult()
    data class Error(val message: String) : SyncResult()
}

private class Reply(val code: Int, val body: String?, val ts: String? = null, val sig: String? = null)

/**
 * Till side of sync. A till sends the sales (and refunds/voids) it queued to the main device, which
 * validates and applies them, then downloads the main device's products and customers, which now
 * include the effect of every till's sales. Products are managed on the main device.
 */
@Singleton
class SyncClient @Inject constructor(
    private val syncDao: SyncDao,
    private val snapshots: SnapshotService
) {
    companion object {
        private const val TAG     = "SyncClient"
        private const val TIMEOUT = 8000
        private const val BATCH   = 40
    }

    // The background worker, the post-sale kick and the Sync Now button must never overlap.
    private val syncLock = Mutex()

    suspend fun sync(serverIp: String, deviceId: String, peerKey: String): SyncResult = syncLock.withLock { withContext(Dispatchers.IO) {
        try {
            if (!LanAddress.isPrivate(serverIp))
                return@withContext SyncResult.Error("Enter the main phone's Wi-Fi address (for example 192.168.1.20). Sync only works on your own network.")
            val baseUrl = "http://${serverIp.trim()}:${SyncServer.PORT}"

            val ping = request("GET", "$baseUrl/ping", null, null)
            if (ping == null || ping.code != 200) return@withContext SyncResult.Error("Main device unreachable at $serverIp")

            // 1. Push queued sales / refunds / voids, oldest first, in small batches.
            var pushed = 0
            var rejected = 0
            val reasons = mutableListOf<String>()
            val pending = syncDao.getPendingLogs()
            for (batch in pending.chunked(BATCH)) {
                val arr = JSONArray()
                batch.forEach { log ->
                    arr.put(JSONObject().apply {
                        put("id", log.id); put("entityType", log.entityType.name)
                        put("entityId", log.entityId); put("operation", log.operation.name)
                        put("deviceId", log.deviceId); put("payload", log.payload)
                        put("createdAt", log.createdAt)
                    })
                }
                val reply = request("POST", "$baseUrl/apply", arr.toString(), peerKey)
                    ?: return@withContext SyncResult.Error("Lost connection to the main device")
                authError(reply)?.let { return@withContext SyncResult.Error(it) }
                if (reply.code != 200 || reply.body == null) return@withContext SyncResult.Error("Main device error (${reply.code})")
                replyProblem(reply, "/apply", peerKey)?.let { return@withContext SyncResult.Error(it) }
                val replyText = SyncCrypto.open(peerKey, SyncCrypto.responseAad("/apply", reply.ts.orEmpty()), reply.body)
                    ?: return@withContext SyncResult.Error("Could not decrypt the main device's reply — check the pairing code")
                val results = JSONObject(replyText).optJSONArray("results")
                    ?: return@withContext SyncResult.Error("Main device is running an older version — update it first")
                val done = mutableListOf<Long>()
                for (i in 0 until results.length()) {
                    val r = results.getJSONObject(i)
                    val id = r.optLong("id", -1)
                    when (r.optString("status")) {
                        "ok" -> { pushed++; done += id }
                        "duplicate" -> done += id
                        else -> {
                            rejected++
                            reasons += r.optString("error", "rejected")
                            syncDao.updateStatus(id, SyncStatus.CONFLICT)
                        }
                    }
                }
                if (done.isNotEmpty()) syncDao.markSynced(done)
            }

            // 2. Pull the main device's catalogue and customers — only when nothing of ours is still
            //    waiting, so its stock figures can't overwrite a sale it hasn't seen yet.
            if (syncDao.countPending() > 0) {
                return@withContext SyncResult.Error("Some sales are still waiting to be sent; try again")
            }
            val snap = request("GET", "$baseUrl/snapshot", null, peerKey)
                ?: return@withContext SyncResult.Error("Lost connection to the main device")
            authError(snap)?.let { return@withContext SyncResult.Error(it) }
            val body = snap.body
            if (snap.code != 200 || body == null) return@withContext SyncResult.Error("Main device error (${snap.code})")
            replyProblem(snap, "/snapshot", peerKey)?.let { return@withContext SyncResult.Error(it) }
            val snapText = SyncCrypto.open(peerKey, SyncCrypto.responseAad("/snapshot", snap.ts.orEmpty()), body)
                ?: return@withContext SyncResult.Error("Could not decrypt the main device's reply — check the pairing code")
            val applied = snapshots.apply(snapText)

            // 3. Prune old synced logs (keep last 7 days)
            syncDao.pruneOldLogs(System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L)

            SyncResult.Success(pushed, rejected, applied.products, applied.customers, reasons.distinct().take(3))
        } catch (e: Exception) {
            Log.e(TAG, "Sync error", e)
            SyncResult.Error(e.localizedMessage ?: "Sync failed")
        }
    } }

    /** The reply must carry the main device's signature; an unsigned or altered reply is never applied. */
    private fun replyProblem(r: Reply, path: String, peerKey: String): String? {
        val ts = r.ts ?: return "Reply could not be verified"
        if (r.sig.isNullOrBlank())
            return "The main device did not sign its reply — update the app on both phones to the latest version"
        return if (SyncAuth.verifyResponse(peerKey, path, ts, r.body.orEmpty(), r.sig)) null
        else "The reply did not come from the paired main device — check you are on your own Wi-Fi"
    }

    private fun authError(r: Reply): String? = when (r.code) {
        401 -> "Pairing code rejected — check the code, that both phones run the latest app, and that both clocks are correct"
        426 -> "The main phone needs the latest app: sync is now encrypted, so both phones must be updated"
        429 -> "Too many wrong codes — wait 30 seconds and try again"
        else -> null
    }

    private fun request(method: String, url: String, body: String?, syncKey: String?): Reply? = try {
        val target = URL(url)
        val path = target.path
        val headers = LinkedHashMap<String, String>()
        var reqTs: String? = null
        // With a pairing code the body is encrypted, then signed, so what is signed is what is sent.
        var wireBody = body
        syncKey?.let {
            val ts = System.currentTimeMillis()
            reqTs = ts.toString()
            if (body != null) wireBody = SyncCrypto.seal(it, SyncCrypto.requestAad(method, path, ts.toString()), body)
            headers["X-Sync-Enc"] = "1"
            headers["X-Sync-Ts"] = ts.toString()
            headers["X-Sync-Sig"] = SyncAuth.sign(it, method, path, wireBody ?: "", ts)
        }
        if (wireBody != null) headers["Content-Type"] = "application/json"
        // Plain sockets, not HttpURLConnection: cleartext HTTP is blocked app-wide (network_security_config).
        val r = LanHttp.request(target.host, target.port, method, path, headers, wireBody, TIMEOUT, TIMEOUT * 4)
        Reply(r.code, r.body, ts = reqTs, sig = r.header("X-Sync-Resp-Sig"))
    } catch (_: Exception) { null }
}
