package com.minimart.pos.cloud

import android.content.SharedPreferences
import com.minimart.pos.di.SecurePrefs
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Small bits of cloud state kept on this phone (encrypted preferences). Being signed in with Google is what "connected" means. */
@Singleton
class CloudConfig @Inject constructor(@SecurePrefs private val prefs: SharedPreferences, private val auth: OwnerAuth) {
    val isConfigured: Boolean get() = auth.signedIn

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

    /** Identifies this shop in the owner's account; generated once. */
    val storeId: String
        get() = prefs.getString("cloud_store_id", null)
            ?: UUID.randomUUID().toString().also { prefs.edit().putString("cloud_store_id", it).apply() }

    /** Forgets this phone's cloud state. Also clears settings left by the old Supabase connection. */
    fun disconnect() {
        prefs.edit().remove("cloud_last_push").remove("cloud_store_id").remove("catalog_role").remove("catalog_version")
            .remove("catalog_fp").remove("catalog_at")
            .remove("cloud_registered").remove("cloud_url").remove("cloud_anon_key").remove("cloud_store_secret").remove("cloud_mpesa_token").apply()
    }
}
