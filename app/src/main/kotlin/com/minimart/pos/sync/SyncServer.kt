package com.minimart.pos.sync

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.minimart.pos.data.dao.SyncDao
import com.minimart.pos.data.entity.SyncLog
import com.minimart.pos.data.entity.SyncStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.net.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncServer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val syncDao: SyncDao
) {
    companion object {
        const val PORT       = 9876
        const val SERVICE    = "MiniMartPOS"
        private const val TAG = "SyncServer"
    }

    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    fun start() {
        if (_isRunning.value) return
        serverJob = scope.launch {
            try {
                serverSocket = ServerSocket(PORT)
                _isRunning.value = true
                Log.i(TAG, "Sync server started on port $PORT (${getLocalIp()})")
                while (isActive) {
                    val client = serverSocket?.accept() ?: break
                    launch { handleClient(client) }
                }
            } catch (e: Exception) {
                if (_isRunning.value) Log.e(TAG, "Server error", e)
            } finally {
                _isRunning.value = false
            }
        }
    }

    fun stop() {
        _isRunning.value = false
        serverSocket?.close()
        serverSocket = null
        serverJob?.cancel()
    }

    private suspend fun handleClient(socket: Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val writer = PrintWriter(socket.getOutputStream(), true)

            val requestLine = reader.readLine() ?: return
            val headers = mutableMapOf<String, String>()
            var line: String?
            var contentLength = 0
            while (reader.readLine().also { line = it } != null && line!!.isNotEmpty()) {
                val parts = line!!.split(": ", limit = 2)
                if (parts.size == 2) {
                    headers[parts[0]] = parts[1]
                    if (parts[0] == "Content-Length") contentLength = parts[1].toIntOrNull() ?: 0
                }
            }

            val method = requestLine.split(" ").firstOrNull() ?: "GET"
            val path   = requestLine.split(" ").getOrNull(1) ?: "/"

            when {
                method == "GET" && path == "/ping" -> {
                    respond(writer, 200, """{"status":"ok","service":"MiniMartPOS"}""")
                }
                method == "GET" && path == "/changes" -> {
                    val pending = syncDao.getPendingLogs()
                    val arr = JSONArray()
                    pending.forEach { log ->
                        arr.put(JSONObject().apply {
                            put("id", log.id); put("entityType", log.entityType.name)
                            put("entityId", log.entityId); put("operation", log.operation.name)
                            put("deviceId", log.deviceId); put("payload", log.payload)
                            put("createdAt", log.createdAt)
                        })
                    }
                    respond(writer, 200, arr.toString())
                }
                method == "POST" && path == "/apply" -> {
                    val body = CharArray(contentLength).also { reader.read(it) }.concatToString()
                    val arr  = JSONArray(body)
                    val ids  = mutableListOf<Long>()
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val log = SyncLog(
                            entityType = com.minimart.pos.data.entity.SyncEntityType.valueOf(obj.getString("entityType")),
                            entityId   = obj.getLong("entityId"),
                            operation  = com.minimart.pos.data.entity.SyncOperation.valueOf(obj.getString("operation")),
                            deviceId   = obj.getString("deviceId"),
                            payload    = obj.getString("payload"),
                            status     = SyncStatus.SYNCED,
                            createdAt  = obj.getLong("createdAt")
                        )
                        val insertedId = syncDao.insertLog(log)
                        ids.add(insertedId)
                    }
                    respond(writer, 200, """{"applied":${ids.size}}""")
                }
                else -> respond(writer, 404, """{"error":"Not found"}""")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Client handler error", e)
        } finally {
            socket.close()
        }
    }

    private fun respond(writer: PrintWriter, code: Int, body: String) {
        val status = if (code == 200) "OK" else "Error"
        writer.println("HTTP/1.1 $code $status")
        writer.println("Content-Type: application/json")
        writer.println("Content-Length: ${body.toByteArray().size}")
        writer.println()
        writer.println(body)
        writer.flush()
    }

    fun getLocalIp(): String {
        return try {
            val wm = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val ip = wm?.connectionInfo?.ipAddress ?: 0
            "${ip and 0xff}.${ip shr 8 and 0xff}.${ip shr 16 and 0xff}.${ip shr 24 and 0xff}"
        } catch (_: Exception) { "unknown" }
    }
}
