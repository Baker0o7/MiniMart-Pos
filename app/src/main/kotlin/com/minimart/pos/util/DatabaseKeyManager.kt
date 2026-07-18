package com.minimart.pos.util

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generates and retrieves a 256-bit AES database encryption key, wrapped
 * (encrypted) with a key that lives in the Android Keystore hardware module.
 *
 * Flow:
 *   1. On first launch: generate a random 32-byte DB key, encrypt it with an
 *      AES-GCM key from the Keystore, store the ciphertext in SharedPreferences.
 *   2. On subsequent launches: load the ciphertext, decrypt with the Keystore key,
 *      return the plaintext DB key to SQLCipher.
 *
 * The plaintext DB key never touches disk — only the AES-GCM-encrypted blob is
 * stored. The Keystore key itself is hardware-backed on supported devices and cannot
 * be extracted, so even if someone pulls the SharedPreferences file from a rooted
 * device, they can't decrypt the DB key without the Keystore hardware.
 */
@Singleton
class DatabaseKeyManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val KEYSTORE_ALIAS  = "minimart_db_key_v1"
        private const val PREFS_FILE      = "minimart_db_enc_prefs"
        private const val PREFS_KEY_IV    = "enc_iv"
        private const val PREFS_KEY_BLOB  = "enc_key_blob"
        private const val KEY_SIZE_BITS   = 256
        private const val GCM_TAG_BITS    = 128
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    }

    /**
     * Returns the 32-byte raw key for SQLCipher's SupportFactory.
     * Generates and stores the key on first call.
     */
    fun getRawDbKey(): ByteArray {
        val prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
        val existingBlob = prefs.getString(PREFS_KEY_BLOB, null)

        return if (existingBlob != null) {
            val iv   = Base64.decode(prefs.getString(PREFS_KEY_IV, "")!!, Base64.NO_WRAP)
            val blob = Base64.decode(existingBlob, Base64.NO_WRAP)
            val key  = getOrCreateKeystoreKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.doFinal(blob)
        } else {
            val rawKey = ByteArray(KEY_SIZE_BITS / 8).also {
                java.security.SecureRandom().nextBytes(it)
            }
            val keystoreKey = getOrCreateKeystoreKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, keystoreKey)
            val blob = cipher.doFinal(rawKey)
            prefs.edit()
                .putString(PREFS_KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                .putString(PREFS_KEY_BLOB, Base64.encodeToString(blob, Base64.NO_WRAP))
                .apply()
            rawKey
        }
    }

    /**
     * Returns the key as a hex string (used by DbEncryptionMigrator's SQL ATTACH KEY).
     */
    fun getOrCreateDbKey(): String = getRawDbKey().toHexString()

    private fun getOrCreateKeystoreKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).also { it.load(null) }
        val existing = ks.getEntry(KEYSTORE_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey

        val spec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setKeySize(KEY_SIZE_BITS)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(false) // no biometric gate on the DB key itself
            .build()

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .also { it.init(spec) }
            .generateKey()
    }

    private fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }
}
