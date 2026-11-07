package com.minimart.pos.cloud

import android.content.SharedPreferences
import com.minimart.pos.di.SecurePrefs
import java.security.SecureRandom
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Connection details for the owner's own Supabase project; kept in encrypted preferences. */
@Singleton
class CloudConfig @Inject constructor(@SecurePrefs private val prefs: SharedPreferences) {
    /** The operator's hosted backend is built into this app (see app/build.gradle.kts), so owners need no project of their own. */
    val hostedAvailable: Boolean get() = com.minimart.pos.BuildConfig.CLOUD_URL.startsWith("https://") && com.minimart.pos.BuildConfig.CLOUD_ANON_KEY.isNotBlank()

    /** Hosted mode unless this phone was already connected to an owner's own project (they keep working as before). */
    val usesHosted: Boolean get() {
        if (!hostedAvailable) return false
        val saved = prefs.getString("cloud_url", "") ?: ""
        return !registered || saved.isBlank() || saved.trimEnd('/') == com.minimart.pos.BuildConfig.CLOUD_URL.trimEnd('/')
    }

    var url: String
        get() = if (usesHosted) com.minimart.pos.BuildConfig.CLOUD_URL.trim().trimEnd('/') else (prefs.getString("cloud_url", "") ?: "")
        set(v) { prefs.edit().putString("cloud_url", v.trim().trimEnd('/')).apply() }
    var anonKey: String
        get() = if (usesHosted) com.minimart.pos.BuildConfig.CLOUD_ANON_KEY else (prefs.getString("cloud_anon_key", "") ?: "")
        set(v) { prefs.edit().putString("cloud_anon_key", v.trim()).apply() }
    var registered: Boolean
        get() = prefs.getBoolean("cloud_registered", false)
        set(v) { prefs.edit().putBoolean("cloud_registered", v).apply() }
    var lastPushAt: Long
        get() = prefs.getLong("cloud_last_push", 0L)
        set(v) { prefs.edit().putLong("cloud_last_push", v).apply() }

    /** Shared product list: 0 = off, 1 = this shop is the main shop and publishes, 2 = this shop follows the main shop. */
    var catalogRole: Int
        get() = prefs.getInt("catalog_role", 0)
        set(v) { prefs.edit().putInt("catalog_role", v.coerceIn(0, 2)).apply() }
    var catalogVersion: Long
        get() = prefs.getLong("catalog_version", 0L)
        set(v) { prefs.edit().putLong("catalog_version", v).apply() }
    var catalogFingerprint: String
        get() = prefs.getString("catalog_fp", "") ?: ""
        set(v) { prefs.edit().putString("catalog_fp", v).apply() }
    var catalogAt: Long
        get() = prefs.getLong("catalog_at", 0L)
        set(v) { prefs.edit().putLong("catalog_at", v).apply() }

    /** Secret part of this store's M-Pesa webhook address. Empty when instant M-Pesa is off. */
    var mpesaWebhookToken: String
        get() = prefs.getString("cloud_mpesa_token", "") ?: ""
        set(v) { prefs.edit().putString("cloud_mpesa_token", v.trim()).apply() }

    /** Identifies this store in the cloud; generated once. */
    val storeId: String
        get() = prefs.getString("cloud_store_id", null)
            ?: UUID.randomUUID().toString().also { prefs.edit().putString("cloud_store_id", it).apply() }

    /** Proves a push comes from this store. Random, generated once, never shown; only a hash is stored online. */
    val storeSecret: String
        get() = prefs.getString("cloud_store_secret", null) ?: run {
            val bytes = ByteArray(24).also { SecureRandom().nextBytes(it) }
            bytes.joinToString("") { "%02x".format(it) }.also { prefs.edit().putString("cloud_store_secret", it).apply() }
        }

    val isConfigured: Boolean get() = registered && url.startsWith("https://") && anonKey.isNotBlank()

    fun disconnect() {
        prefs.edit().remove("cloud_registered").remove("cloud_last_push").remove("cloud_store_id").remove("cloud_store_secret").remove("cloud_mpesa_token").remove("catalog_role").remove("catalog_version").remove("catalog_fp").remove("catalog_at").apply()
    }
}
