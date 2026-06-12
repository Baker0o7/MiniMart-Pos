package com.minimart.pos.sync

import android.util.Log
import com.minimart.pos.data.dao.SyncDao
import com.minimart.pos.data.entity.SyncLog
import com.minimart.pos.data.entity.SyncOperation
import com.minimart.pos.data.entity.SyncEntityType
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
    data class Success(val pushed: Int, val pulled: Int) : SyncResult()
    data class Error(val message: String) : SyncResult()
}

@Singleton
class SyncClient @Inject constructor(
    private val syncDao: SyncDao
) {
    companion object {
        private const val TAG     = "SyncClient"
        private const val TIMEOUT = 5000
    }

    /** Full sync cycle: push local pending → pull remote pending */
    suspend fun sync(serverIp: String, deviceId: String): SyncResult = withContext(Dispatchers.IO) {
        try {
            val baseUrl = "http://$serverIp:${SyncServer.PORT}"

            // 1. Ping
            val ping = get("$baseUrl/ping") ?: return@withContext SyncResult.Error("Server unreachable at $serverIp")
            Log.d(TAG, "Ping: $ping")

            // 2. Push local pending changes
            val pending = syncDao.getPendingLogs()
            var pushed = 0
            if (pending.isNotEmpty()) {
                val arr = JSONArray()
                pending.forEach { log ->
                    arr.put(JSONObject().apply {
                        put("id", log.id); put("entityType", log.entityType.name)
                        put("entityId", log.entityId); put("operation", log.operation.name)
                        put("deviceId", log.deviceId); put("payload", log.payload)
                        put("createdAt", log.createdAt)
                    })
                }
                val pushResponse = post("$baseUrl/apply", arr.toString())
                if (pushResponse != null) {
                    val applied = JSONObject(pushResponse).optInt("applied", 0)
                    syncDao.markSynced(pending.map { it.id })
                    pushed = applied
                    Log.i(TAG, "Pushed $pushed changes")
                }
            }

            // 3. Pull remote pending changes
            val remoteChanges = get("$baseUrl/changes")
            var pulled = 0
            if (remoteChanges != null) {
                val arr = JSONArray(remoteChanges)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val remoteDeviceId = obj.getString("deviceId")
                    if (remoteDeviceId == deviceId) continue // skip own changes
                    val log = SyncLog(
                        entityType = SyncEntityType.valueOf(obj.getString("entityType")),
                        entityId   = obj.getLong("entityId"),
                        operation  = SyncOperation.valueOf(obj.getString("operation")),
                        deviceId   = remoteDeviceId,
                        payload    = obj.getString("payload"),
                        status     = SyncStatus.SYNCED,
                        createdAt  = obj.getLong("createdAt")
                    )
                    syncDao.insertLog(log)
                    pulled++
                }
                Log.i(TAG, "Pulled $pulled changes")
            }

            // 4. Prune old synced logs (keep last 7 days)
            syncDao.pruneOldLogs(System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L)

            SyncResult.Success(pushed, pulled)
        } catch (e: Exception) {
            Log.e(TAG, "Sync error", e)
            SyncResult.Error(e.localizedMessage ?: "Sync failed")
        }
    }

    private fun get(url: String): String? = try {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = TIMEOUT; conn.readTimeout = TIMEOUT
        conn.requestMethod = "GET"
        if (conn.responseCode == 200) conn.inputStream.bufferedReader().readText()
        else null
    } catch (_: Exception) { null }

    private fun post(url: String, body: String): String? = try {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = TIMEOUT; conn.readTimeout = TIMEOUT
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Content-Length", body.toByteArray().size.toString())
        conn.outputStream.write(body.toByteArray())
        conn.outputStream.flush()
        if (conn.responseCode == 200) conn.inputStream.bufferedReader().readText()
        else null
    } catch (_: Exception) { null }
}
