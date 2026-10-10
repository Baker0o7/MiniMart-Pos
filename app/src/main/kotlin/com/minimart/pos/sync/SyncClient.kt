package com.minimart.pos.sync

import android.util.Log
import com.minimart.pos.data.dao.SyncDao
import com.minimart.pos.data.entity.SyncStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

sealed class SyncResult {
    /** [pushed] sales the main device accepted, [rejected] it refused, then what changed locally. */
    data class Success(val pushed: Int, val rejected: Int, val products: Int, val customers: Int, val rejectedReasons: List<String> = emptyList()) : SyncResult()
    data class Error(val message: String) : SyncResult()
}

private class Reply(val code: Int, val body: String?)

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

    suspend fun sync(serverIp: String, deviceId: String, peerKey: String): SyncResult = withContext(Dispatchers.IO) {
        try {
            val baseUrl = "http://$serverIp:${SyncServer.PORT}"

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
                val results = JSONObject(reply.body).optJSONArray("results")
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
            val applied = snapshots.apply(body)

            // 3. Prune old synced logs (keep last 7 days)
            syncDao.pruneOldLogs(System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L)

            SyncResult.Success(pushed, rejected, applied.products, applied.customers, reasons.distinct().take(3))
        } catch (e: Exception) {
            Log.e(TAG, "Sync error", e)
            SyncResult.Error(e.localizedMessage ?: "Sync failed")
        }
    }

    private fun authError(r: Reply): String? = when (r.code) {
        401 -> "Pairing code rejected — check the code on the main device"
        429 -> "Too many wrong codes — wait 30 seconds and try again"
        else -> null
    }

    private fun request(method: String, url: String, body: String?, syncKey: String?): Reply? = try {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = TIMEOUT; conn.readTimeout = TIMEOUT * 4
        conn.requestMethod = method
        syncKey?.let { conn.setRequestProperty("X-Sync-Key", it) }
        if (body != null) {
            val bytes = body.toByteArray(Charsets.UTF_8)
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setFixedLengthStreamingMode(bytes.size)
            conn.outputStream.use { it.write(bytes) }
        }
        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
        Reply(code, text)
    } catch (_: Exception) { null }
}
