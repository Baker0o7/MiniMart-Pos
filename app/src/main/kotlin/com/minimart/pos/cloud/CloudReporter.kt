package com.minimart.pos.cloud

import com.minimart.pos.data.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

sealed class CloudResult {
    object Ok : CloudResult()
    data class Failed(val message: String) : CloudResult()
}

/** Registers this store with the owner's Supabase project and uploads its summary. */
@Singleton
class CloudReporter @Inject constructor(
    private val config: CloudConfig,
    private val builder: CloudSnapshotBuilder,
    private val settings: SettingsRepository
) {
    private val lock = Mutex()   // one upload at a time (worker, shift close and the button can overlap)

    suspend fun register(setupCode: String): CloudResult = withContext(Dispatchers.IO) {
        if (!config.url.startsWith("https://")) return@withContext CloudResult.Failed("The project URL must start with https://")
        if (config.anonKey.isBlank()) return@withContext CloudResult.Failed("Enter the project's anon key")
        if (setupCode.isBlank()) return@withContext CloudResult.Failed("Enter the setup code from your SQL script")
        val name = try { settings.storeName.first() } catch (_: Exception) { "Store" }
        val body = JSONObject().put("p_setup_code", setupCode.trim()).put("p_store_id", config.storeId)
            .put("p_name", name).put("p_secret", config.storeSecret)
        when (val r = rpc("register_store", body)) {
            is CloudResult.Ok -> { config.registered = true; CloudResult.Ok }
            // Registering twice (e.g. after an interrupted first try) is fine.
            is CloudResult.Failed -> if (r.message.contains("already registered", true)) { config.registered = true; CloudResult.Ok } else r
        }
    }

    suspend fun push(): CloudResult = lock.withLock {
        if (!config.isConfigured) return CloudResult.Failed("Cloud dashboard is not connected")
        withContext(Dispatchers.IO) {
            try {
                val body = JSONObject().put("p_store_id", config.storeId).put("p_secret", config.storeSecret)
                    .put("p_payload", builder.build())
                val r = rpc("push_snapshot", body)
                if (r is CloudResult.Ok) config.lastPushAt = System.currentTimeMillis()
                r
            } catch (e: Exception) {
                CloudResult.Failed(e.localizedMessage ?: "Upload failed")
            }
        }
    }

    private fun rpc(fn: String, body: JSONObject): CloudResult = try {
        val conn = URL("${config.url}/rest/v1/rpc/$fn").openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000; conn.readTimeout = 30_000
        conn.requestMethod = "POST"
        conn.setRequestProperty("apikey", config.anonKey)
        conn.setRequestProperty("Authorization", "Bearer ${config.anonKey}")
        conn.setRequestProperty("Content-Type", "application/json")
        val bytes = body.toString().toByteArray(Charsets.UTF_8)
        conn.doOutput = true
        conn.setFixedLengthStreamingMode(bytes.size)
        conn.outputStream.use { it.write(bytes) }
        val code = conn.responseCode
        if (code in 200..299) CloudResult.Ok
        else {
            val text = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val msg = try { JSONObject(text).optString("message", "") } catch (_: Exception) { "" }
            CloudResult.Failed(msg.ifBlank { "Server replied $code" })
        }
    } catch (e: Exception) {
        CloudResult.Failed(e.localizedMessage ?: "Could not reach the server")
    }
}
