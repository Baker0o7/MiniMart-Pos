package com.minimart.pos.di

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.io.File

/**
 * One-time bridge from the old androidx.security EncryptedSharedPreferences files to [com.minimart.pos.data.security.KeystorePrefs],
 * so shops keep their Daraja keys, pairing codes and cloud settings. The old format can only be read with that
 * library, so this class (and the dependency) is to be deleted once most phones have updated.
 * A failure leaves the old file in place and never stops the app from starting.
 */
internal object LegacySecurePrefsMigrator {
    fun migrate(context: Context, legacyName: String, target: SharedPreferences) {
        val legacyFile = File(context.filesDir.parentFile, "shared_prefs/$legacyName.xml")
        if (!legacyFile.exists()) return
        try {
            val masterKey = androidx.security.crypto.MasterKey.Builder(context)
                .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM).build()
            val old = androidx.security.crypto.EncryptedSharedPreferences.create(
                context, legacyName, masterKey,
                androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
            val e = target.edit()
            old.all.forEach { (k, v) ->
                if (target.contains(k)) return@forEach   // never overwrite something already saved in the new store
                @Suppress("UNCHECKED_CAST")
                when (v) {
                    is String -> e.putString(k, v)
                    is Int -> e.putInt(k, v)
                    is Long -> e.putLong(k, v)
                    is Float -> e.putFloat(k, v)
                    is Boolean -> e.putBoolean(k, v)
                    is Set<*> -> e.putStringSet(k, (v as Set<String>).toMutableSet())
                }
            }
            if (e.commit()) context.deleteSharedPreferences(legacyName)
        } catch (ex: Exception) {
            Log.w("SecureStorage", "Could not carry over $legacyName; will retry next start", ex)
        }
    }
}
