package com.minimart.pos.cloud

import com.minimart.pos.data.repository.CatalogApplier
import com.minimart.pos.util.CatalogRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

sealed class CatalogOutcome {
    /** [detail] is a plain sentence for the owner. */
    data class Done(val detail: String) : CatalogOutcome()
    data class Failed(val message: String, val anotherPublisher: Boolean = false) : CatalogOutcome()
}

/**
 * Shares the product list between an owner's shops over the internet. One shop (the main shop) publishes; the
 * others follow. Only names, prices, tax and supplier details travel: never stock, sales or customers, so a
 * follower's till and shelf counts are never touched. Everything received is checked by `CatalogRules` first.
 */
@Singleton
class CatalogSync @Inject constructor(private val config: CloudConfig, private val applier: CatalogApplier) {
    private val lock = Mutex()

    /** Sends this shop's list when it changed (or when [force]). A different shop already publishing needs [takeOver]. */
    suspend fun publish(takeOver: Boolean = false, force: Boolean = false): CatalogOutcome = lock.withLock {
        if (!config.isConfigured) return CatalogOutcome.Failed("Connect the cloud dashboard first.")
        try {
            val items = CatalogRules.sanitize(applier.export()).items
            val fp = CatalogRules.fingerprint(items)
            if (!force && !takeOver && fp == config.catalogFingerprint) return CatalogOutcome.Done("The shared product list is already up to date.")
            val body = JSONObject().put("p_store_id", config.storeId).put("p_secret", config.storeSecret)
                .put("p_items", CatalogJson.toJson(items)).put("p_take_over", takeOver)
            val r = call("publish_catalog", body)
            if (r.code !in 200..299) {
                val msg = message(r.text)
                return CatalogOutcome.Failed(msg.ifBlank { "Server replied ${r.code}" }, anotherPublisher = msg.contains("already publishes", true))
            }
            config.catalogVersion = r.text.trim().toLongOrNull() ?: config.catalogVersion
            config.catalogFingerprint = fp; config.catalogAt = System.currentTimeMillis()
            CatalogOutcome.Done("Shared ${items.size} products with your other shops.")
        } catch (e: Exception) {
            CatalogOutcome.Failed(com.minimart.pos.util.ErrorAdvice.explain(e, "Sharing the product list"))
        }
    }

    /** Gets the main shop's list if there is a newer one and applies it to this shop's products. */
    suspend fun pull(): CatalogOutcome = lock.withLock {
        if (!config.isConfigured) return CatalogOutcome.Failed("Connect the cloud dashboard first.")
        try {
            val body = JSONObject().put("p_store_id", config.storeId).put("p_secret", config.storeSecret).put("p_since", config.catalogVersion)
            val r = call("fetch_catalog", body)
            if (r.code !in 200..299) return CatalogOutcome.Failed(message(r.text).ifBlank { "Server replied ${r.code}" })
            val rows = JSONArray(r.text)
            if (rows.length() == 0) { config.catalogAt = System.currentTimeMillis(); return CatalogOutcome.Done("Already up to date.") }
            val row = rows.getJSONObject(0)
            val version = row.optLong("version", -1L)
            if (version < 0) return CatalogOutcome.Failed("The server's answer was not understood, so nothing was changed.")
            if (row.optString("publisher_id", "").equals(config.storeId, ignoreCase = true))
                return CatalogOutcome.Failed("This shop is the one that publishes the list, so it does not need to receive it.")
            val parsed = CatalogJson.parse(row.optJSONArray("items"))
            val clean = CatalogRules.sanitize(parsed.items)
            val declared = row.optInt("item_count", parsed.items.size)
            val bad = parsed.skipped + clean.rejected
            // A list that is empty, or mostly unreadable, is treated as damaged: change nothing rather than half-apply it.
            if (declared > 0 && (clean.items.isEmpty() || bad * 5 > declared))
                return CatalogOutcome.Failed("The shared list looks damaged ($bad of $declared products unreadable), so nothing was changed.")
            val result = applier.apply(clean.items)
            config.catalogVersion = version; config.catalogAt = System.currentTimeMillis()
            CatalogOutcome.Done("Product list updated: ${result.added} new, ${result.updated} changed, ${result.deactivated} hidden." +
                if (bad > 0) " $bad unreadable products were skipped." else "")
        } catch (e: Exception) {
            CatalogOutcome.Failed(com.minimart.pos.util.ErrorAdvice.explain(e, "Getting the product list"))
        }
    }

    /** What the background upload should do for this shop's role, quietly. */
    suspend fun syncInBackground() {
        when (config.catalogRole) { 1 -> publish(); 2 -> pull() }
    }

    private class Reply(val code: Int, val text: String)

    private suspend fun call(fn: String, body: JSONObject): Reply = withContext(Dispatchers.IO) {
        val conn = URL("${config.url}/rest/v1/rpc/$fn").openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000; conn.readTimeout = 60_000; conn.requestMethod = "POST"
            conn.setRequestProperty("apikey", config.anonKey); conn.setRequestProperty("Authorization", "Bearer ${config.anonKey}")
            conn.setRequestProperty("Content-Type", "application/json")
            val bytes = body.toString().toByteArray(Charsets.UTF_8)
            conn.doOutput = true; conn.setFixedLengthStreamingMode(bytes.size); conn.outputStream.use { it.write(bytes) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            Reply(code, stream?.use { readCapped(it) }.orEmpty())
        } finally { conn.disconnect() }
    }

    /** Never read more than 8 MB from the network, whatever the server sends. */
    private fun readCapped(input: java.io.InputStream): String {
        val out = java.io.ByteArrayOutputStream(); val buf = ByteArray(16 * 1024); var total = 0
        while (true) {
            val n = input.read(buf); if (n < 0) break
            total += n; if (total > 8 * 1024 * 1024) throw java.io.IOException("The answer from the server was too large")
            out.write(buf, 0, n)
        }
        return out.toString(Charsets.UTF_8.name())
    }

    private fun message(text: String): String = try { JSONObject(text).optString("message", "") } catch (_: Exception) { "" }
}
