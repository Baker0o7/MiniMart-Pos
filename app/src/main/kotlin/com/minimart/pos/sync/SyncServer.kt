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
    private val syncDao: SyncDao,
    private val settingsRepo: com.minimart.pos.data.repository.SettingsRepository
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
            var contentLength = 0
            // Bug fix: the original pattern was `while(readLine().also{line=it}!=null && line!!.isNotEmpty())`
            // which double-derefs line!! after the null check — in theory safe due to single-thread
            // execution, but the Kotlin compiler doesn't know that, and any refactor risks an NPE.
            // Eliminated both !! by using a local `currentLine` val inside the loop instead.
            loop@ while (true) {
                val currentLine = reader.readLine() ?: break@loop
                if (currentLine.isEmpty()) break@loop
                val parts = currentLine.split(": ", limit = 2)
                if (parts.size == 2) {
                    headers[parts[0]] = parts[1]
                    if (parts[0] == "Content-Length") contentLength = parts[1].toIntOrNull() ?: 0
                }
            }

            val method = requestLine.split(" ").firstOrNull() ?: "GET"
            val path   = requestLine.split(" ").getOrNull(1) ?: "/"

            // Bug fix: previously NO authentication existed on this server at all — any
            // device on the same WiFi network (a customer, a neighboring shop, anyone)
            // could read pending sync data or inject arbitrary fabricated entries via
            // /apply with zero barrier. /ping stays open (it reveals nothing but "a
            // MiniMart POS server exists here"); /changes and /apply now require a
            // matching X-Sync-Key header — the pairing secret shown on this device's
            // Settings screen, which must be typed into the other device once.
            val requiresAuth = path == "/changes" || path == "/apply"
            if (requiresAuth) {
                val mySecret = settingsRepo.getOrCreateSyncSecret()
                val providedKey = headers["X-Sync-Key"]
                if (providedKey != mySecret) {
                    respond(writer, 401, """{"error":"Unauthorized — pairing key mismatch"}""")
                    return
                }
            }

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
                    // Bug fix: a single reader.read(buf) call is NOT guaranteed to fill the
                    // buffer — over a real TCP/WiFi connection it commonly returns fewer
                    // characters than requested for larger payloads, silently truncating the
                    // JSON body. JSONArray(body) would then throw on the malformed/incomplete
                    // array, which the outer catch swallows with no response sent back —
                    // leaving the sync client to see a confusing generic failure instead of
                    // the real cause. Loop until all contentLength chars are read (or EOF).
                    val buf = CharArray(contentLength)
                    var readTotal = 0
                    while (readTotal < contentLength) {
                        val n = reader.read(buf, readTotal, contentLength - readTotal)
                        if (n == -1) break // peer closed early
                        readTotal += n
                    }
                    val body = String(buf, 0, readTotal)
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
            // Bug fix: previously the exception was only logged — the client's connection
            // was closed with no HTTP response at all, making failures (e.g. a truncated
            // body, bad JSON) indistinguishable from "server unreachable" on the client side.
            try {
                val writer = PrintWriter(socket.getOutputStream(), true)
                respond(writer, 500, """{"error":"${(e.message ?: "Internal error").replace("\"", "'")}"}""")
            } catch (_: Exception) { /* socket already broken, nothing more we can do */ }
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
