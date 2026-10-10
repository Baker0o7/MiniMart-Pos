package com.minimart.pos.sync

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withPermit
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
    private val applier: SyncApplier,
    private val snapshots: SnapshotService,
    private val settingsRepo: com.minimart.pos.data.repository.SettingsRepository
) {
    companion object {
        const val PORT       = 9876
        const val SERVICE    = "MiniMartPOS"
        private const val TAG = "SyncServer"
        // Bug fix: there was no rate limiting on the pairing-key check at all. A
        // 6-digit code has only 1,000,000 combinations — with unlimited attempts at
        // LAN speed, an attacker on the same WiFi could brute-force it in minutes.
        private const val MAX_FAILED_ATTEMPTS = 5
        private const val LOCKOUT_DURATION_MS = 30_000L
        private const val SOCKET_TIMEOUT_MS = 10_000
        private const val MAX_BODY_BYTES = 5_000_000
    }

    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val connectionLimit = kotlinx.coroutines.sync.Semaphore(8)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // handleClient() runs concurrently (one coroutine per accepted connection), so
    // these use atomics rather than plain vars to stay correct under concurrent access.
    private val failedAuthAttempts = java.util.concurrent.atomic.AtomicInteger(0)
    private val authLockoutUntilMs = java.util.concurrent.atomic.AtomicLong(0)

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    fun start() {
        if (_isRunning.value) return
        serverJob = scope.launch {
            try {
                // reuseAddress lets the server restart straight after a stop() instead of failing
                // with "address already in use" while the old socket lingers.
                serverSocket = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(PORT))
                }
                _isRunning.value = true
                Log.i(TAG, "Sync server started on port $PORT (${getLocalIp()})")
                while (isActive) {
                    val client = serverSocket?.accept() ?: break
                    // Bounded: a flood of connections can't spawn unlimited handler coroutines.
                    launch { connectionLimit.withPermit { handleClient(client) } }
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
            // A peer that connects and then stalls must not hold an IO thread forever.
            socket.soTimeout = SOCKET_TIMEOUT_MS
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
            val requiresAuth = path == "/snapshot" || path == "/apply"
            if (requiresAuth) {
                // Check lockout BEFORE doing any key comparison — rejects immediately
                // during an active lockout window regardless of whether this particular
                // guess happens to be correct.
                val now = System.currentTimeMillis()
                if (now < authLockoutUntilMs.get()) {
                    respond(writer, 429, """{"error":"Too many failed attempts — try again shortly"}""")
                    return
                }
                val mySecret = settingsRepo.getOrCreateSyncSecret()
                val providedKey = headers["X-Sync-Key"]
                // Bug fix: `providedKey != mySecret` is a plain String comparison, which
                // (like the SHA-256 PIN comparison fixed earlier in PinHasher) returns
                // early on the first mismatched character — a timing side-channel that
                // an attacker on the same WiFi network could use to narrow down the
                // 6-digit pairing code character-by-character by measuring response
                // times across repeated guesses. MessageDigest.isEqual does a
                // constant-time comparison regardless of where the first difference is.
                val keysMatch = providedKey != null &&
                    java.security.MessageDigest.isEqual(
                        providedKey.toByteArray(Charsets.UTF_8),
                        mySecret.toByteArray(Charsets.UTF_8)
                    )
                if (!keysMatch) {
                    val attempts = failedAuthAttempts.incrementAndGet()
                    if (attempts >= MAX_FAILED_ATTEMPTS) {
                        authLockoutUntilMs.set(System.currentTimeMillis() + LOCKOUT_DURATION_MS)
                        failedAuthAttempts.set(0)
                    }
                    respond(writer, 401, """{"error":"Unauthorized — pairing key mismatch"}""")
                    return
                }
                // Successful auth — clear any prior failed attempts so a legitimate
                // user's earlier typo doesn't count toward a future lockout.
                failedAuthAttempts.set(0)
            }

            when {
                method == "GET" && path == "/ping" -> {
                    respond(writer, 200, """{"status":"ok","service":"MiniMartPOS"}""")
                }
                method == "GET" && path == "/snapshot" -> {
                    // Catalogue + customers with this device's current stock and balances.
                    respond(writer, 200, snapshots.build())
                }
                method == "POST" && path == "/apply" -> {
                    // Bug fix: a single reader.read(buf) call is NOT guaranteed to fill the
                    // buffer — over a real TCP/WiFi connection it commonly returns fewer
                    // characters than requested for larger payloads, silently truncating the
                    // JSON body. JSONArray(body) would then throw on the malformed/incomplete
                    // array, which the outer catch swallows with no response sent back —
                    // leaving the sync client to see a confusing generic failure instead of
                    // the real cause. Loop until all contentLength chars are read (or EOF).
                    if (contentLength !in 0..MAX_BODY_BYTES) {
                        respond(writer, 413, """{"error":"Payload too large"}""")
                        return
                    }
                    // Content-Length counts BYTES but the reader yields chars: product or
                    // customer names with accents/emoji are multi-byte in UTF-8, so counting
                    // chars made the server wait for data that never came. Count encoded bytes.
                    val sb = StringBuilder()
                    var bytesRead = 0
                    while (bytesRead < contentLength) {
                        val c = reader.read()
                        if (c == -1) break // peer closed early
                        sb.append(c.toChar())
                        bytesRead += when {
                            c < 0x80 -> 1
                            c < 0x800 -> 2
                            Character.isSurrogate(c.toChar()) -> 2 // surrogate pair = 4 bytes
                            else -> 3
                        }
                    }
                    val body = sb.toString()
                    // Each change is applied once and validated; the till gets a verdict per change.
                    val results = applier.apply(JSONArray(body))
                    var applied = 0
                    for (i in 0 until results.length()) if (results.getJSONObject(i).optString("status") == "ok") applied++
                    respond(writer, 200, JSONObject().put("applied", applied).put("results", results).toString())
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
