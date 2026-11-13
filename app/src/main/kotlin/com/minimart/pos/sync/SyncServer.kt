package com.minimart.pos.sync

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.minimart.pos.util.SyncCrypto
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
        private const val SOCKET_TIMEOUT_MS = 10_000
        private const val MAX_BODY_BYTES = 5_000_000
    }

    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val connectionLimit = kotlinx.coroutines.sync.Semaphore(8)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Wrong pairing codes are counted per sending address, so one stranger on the Wi-Fi cannot lock the shop's own
    // tills out. handleClient() runs concurrently; PeerLockout is synchronized.
    private val lockout = com.minimart.pos.util.PeerLockout()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    fun start() {
        // Kept outside backups: a restored copy would carry stale entries from another day.
        SyncAuth.usePersistentCache(java.io.File(context.noBackupFilesDir, "sync-replay.cache"))
        // A second start() while the first is still binding would fail with "address in use" and its
        // finally block would then report the healthy first server as stopped.
        if (_isRunning.value || serverJob?.isActive == true) return
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
        SyncAuth.flushCache()
        _isRunning.value = false
        serverSocket?.close()
        serverSocket = null
        serverJob?.cancel()
    }

    private suspend fun handleClient(socket: Socket) {
        try {
            // A peer that connects and then stalls must not hold an IO thread forever.
            socket.soTimeout = SOCKET_TIMEOUT_MS
            val out = socket.getOutputStream()
            val peer = socket.inetAddress?.hostAddress ?: "unknown"
            // Headers and body together may never exceed the body limit plus a little: a peer that sends an
            // endless header line must not be able to fill the phone's memory.
            val input = java.io.BufferedInputStream(LimitedInputStream(socket.getInputStream(), MAX_BODY_BYTES + 64_000L))
            val request = try {
                com.minimart.pos.util.LanHttp.readRequest(input, MAX_BODY_BYTES)
            } catch (e: com.minimart.pos.util.LanHttp.HttpError) {
                respond(out, e.status, JSONObject().put("error", e.message ?: "Bad request").toString())
                return
            }
            val method = request.method
            val path = request.path
            // Header names are matched without regard to case (see LanHttp.Request.header).
            val headers = object { operator fun get(name: String) = request.header(name) }

            // Bug fix: previously NO authentication existed on this server at all — any
            // device on the same WiFi network could read pending sync data or inject entries.
            // /ping stays open (it reveals nothing but "a MiniMart POS server exists here"); /snapshot and
            // /apply require a signed request made with the pairing secret shown on this device's Settings screen.
            // The body was read as exact bytes above because the request signature covers it.
            var body = request.body
            val requiresAuth = path == "/snapshot" || path == "/apply"
            var mySecretForReply = ""
            if (requiresAuth) {
                // Check lockout BEFORE doing any key comparison — rejects immediately
                // during an active lockout window regardless of whether this particular
                // guess happens to be correct.
                val now = android.os.SystemClock.elapsedRealtime()   // cannot be set back by changing the clock
                val waitMs = lockout.lockedFor(peer, now)
                if (waitMs > 0) {
                    respond(out, 429, JSONObject().put("error", "Too many failed attempts from this phone — try again in ${(waitMs + 999) / 1000} seconds").toString())
                    return
                }
                val mySecret = settingsRepo.getOrCreateSyncSecret()
                // The pairing code itself is never sent: each request carries a timestamp and an
                // HMAC-SHA256 over (timestamp, method, path, body hash). A listener on the Wi-Fi can
                // neither learn the code nor alter or replay a captured request.
                val keysMatch = SyncAuth.verify(
                    secret = mySecret, method = method, path = path, body = body,
                    timestamp = headers["X-Sync-Ts"], signature = headers["X-Sync-Sig"]
                )
                if (!keysMatch) {
                    lockout.recordFailure(peer, android.os.SystemClock.elapsedRealtime())
                    respond(out, 401, """{"error":"Unauthorized — pairing code mismatch or device clock is off by more than 5 minutes"}""")
                    return
                }
                // Right code: a legitimate user's earlier typo does not count toward a future lockout.
                lockout.recordSuccess(peer)
                mySecretForReply = mySecret
                // Bodies and replies travel encrypted. A request without the marker comes from an older app
                // (or from someone who stripped it), so it is refused rather than answered in the clear.
                if (headers["X-Sync-Enc"] != "1") {
                    respond(out, 426, """{"error":"Update the app on both phones: sync is now encrypted"}""")
                    return
                }
                if (method == "POST") {
                    body = SyncCrypto.open(mySecret, SyncCrypto.requestAad(method, path, headers["X-Sync-Ts"].orEmpty()), body)
                        ?: run {
                            respond(out, 400, """{"error":"Could not decrypt the request"}""")
                            return
                        }
                }
            }

            when {
                method == "GET" && path == "/ping" -> {
                    respond(out, 200, """{"status":"ok","service":"MiniMartPOS"}""")
                }
                method == "GET" && path == "/snapshot" -> {
                    // Catalogue + customers with this device's current stock and balances.
                    respond(out, 200, snapshots.build(), signFor = Triple(mySecretForReply, path, headers["X-Sync-Ts"].orEmpty()))
                }
                method == "POST" && path == "/apply" -> {
                    // Each change is applied once and validated; the till gets a verdict per change.
                    val results = applier.apply(JSONArray(body))
                    var applied = 0
                    for (i in 0 until results.length()) if (results.getJSONObject(i).optString("status") == "ok") applied++
                    respond(out, 200, JSONObject().put("applied", applied).put("results", results).toString(),
                        signFor = Triple(mySecretForReply, path, headers["X-Sync-Ts"].orEmpty()))
                }
                else -> respond(out, 404, """{"error":"Not found"}""")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Client handler error", e)
            // Bug fix: previously the exception was only logged — the client's connection
            // was closed with no HTTP response at all, making failures (e.g. a truncated
            // body, bad JSON) indistinguishable from "server unreachable" on the client side.
            try {
                com.minimart.pos.util.ErrorAdvice.explain(e, "A sync request")   // keep the detail on this phone
                respond(socket.getOutputStream(), 500, """{"error":"Internal error"}""")
            } catch (_: Exception) { /* socket already broken, nothing more we can do */ }
        } finally {
            socket.close()
        }
    }

    /** [signFor] = (secret, path, request timestamp): adds a signature the till checks before trusting the reply. */
    private fun respond(out: java.io.OutputStream, code: Int, body: String, signFor: Triple<String, String, String>? = null) {
        // Signed replies are encrypted first, and the signature covers what is actually sent.
        val signed = signFor != null && signFor.first.isNotEmpty()
        val payload = if (signed) SyncCrypto.seal(signFor!!.first, SyncCrypto.responseAad(signFor.second, signFor.third), body) else body
        val extra = if (signed) listOf("X-Sync-Enc" to "1",
            "X-Sync-Resp-Sig" to SyncAuth.signResponse(signFor!!.first, signFor.second, signFor.third, payload)) else emptyList()
        out.write(com.minimart.pos.util.LanHttp.buildResponse(code, payload, extra))
        out.flush()
    }

    fun getLocalIp(): String {
        return try {
            val wm = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val ip = wm?.connectionInfo?.ipAddress ?: 0
            "${ip and 0xff}.${ip shr 8 and 0xff}.${ip shr 16 and 0xff}.${ip shr 24 and 0xff}"
        } catch (_: Exception) { "unknown" }
    }
}

/** Stops reading (as if the peer closed) after [limit] bytes. */
internal class LimitedInputStream(private val inner: java.io.InputStream, private var left: Long) : java.io.InputStream() {
    override fun read(): Int {
        if (left <= 0) return -1
        val b = inner.read()
        if (b >= 0) left--
        return b
    }
    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (left <= 0) return -1
        val n = inner.read(b, off, minOf(len.toLong(), left).toInt())
        if (n > 0) left -= n
        return n
    }
    override fun close() = inner.close()
}
