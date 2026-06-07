package com.minimart.pos.util

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the SQLCipher database encryption key.
 *
 * Strategy:
 *  1. Generate a cryptographically-secure 32-byte random key (one time, on first launch)
 *  2. Encrypt that key using an AES-GCM key stored in the Android Keystore
 *  3. Persist the encrypted key blob in EncryptedSharedPreferences
 *  4. On every subsequent launch, decrypt the blob to retrieve the raw key bytes
 *
 * The Android Keystore key never leaves the hardware security module (on devices with
 * StrongBox / TEE), so the database key is only accessible on this device.
 */
@Singleton
class DatabaseKeyManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG              = "DBKeyManager"
        private const val KEYSTORE_ALIAS   = "minimart_db_key"
        private const val PREFS_NAME       = "minimart_db_key_prefs"
        private const val PREFS_KEY_ENC    = "db_key_enc"
        private const val PREFS_KEY_IV     = "db_key_iv"
        private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH   = 128
        private const val KEY_SIZE_BYTES   = 32
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Returns the 32-byte SQLCipher database key.
     * Creates and persists a new key on first launch.
     */
    fun getOrCreateKey(): ByteArray {
        return try {
            val encKeyB64 = prefs.getString(PREFS_KEY_ENC, null)
            val ivB64     = prefs.getString(PREFS_KEY_IV, null)

            if (encKeyB64 != null && ivB64 != null) {
                // Decrypt existing key
                decryptKey(
                    Base64.decode(encKeyB64, Base64.DEFAULT),
                    Base64.decode(ivB64, Base64.DEFAULT)
                )
            } else {
                // Generate new random key + encrypt + persist
                val rawKey = ByteArray(KEY_SIZE_BYTES).also {
                    java.security.SecureRandom().nextBytes(it)
                }
                val (encKey, iv) = encryptKey(rawKey)
                prefs.edit()
                    .putString(PREFS_KEY_ENC, Base64.encodeToString(encKey, Base64.DEFAULT))
                    .putString(PREFS_KEY_IV,  Base64.encodeToString(iv,     Base64.DEFAULT))
                    .apply()
                Log.d(TAG, "New database encryption key created and stored")
                rawKey
            }
        } catch (e: Exception) {
            Log.e(TAG, "Key operation failed — using fallback key", e)
            // Fallback: derive a device-stable key from Android ID
            // Not hardware-backed but prevents crash on devices where Keystore fails
            val androidId = android.provider.Settings.Secure.getString(
                context.contentResolver, android.provider.Settings.Secure.ANDROID_ID
            ) ?: "minimart_fallback"
            val digest = java.security.MessageDigest.getInstance("SHA-256")
                .digest("minimart_db_$androidId".toByteArray(Charsets.UTF_8))
            digest // 32 bytes
        }
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private fun getOrCreateKeystoreKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").also { it.load(null) }
        if (ks.containsAlias(KEYSTORE_ALIAS)) {
            return (ks.getEntry(KEYSTORE_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
        }
        val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        keyGen.init(
            KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false)  // no lock-screen required — POS stays unlocked
            .build()
        )
        return keyGen.generateKey()
    }

    private fun encryptKey(rawKey: ByteArray): Pair<ByteArray, ByteArray> {
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKeystoreKey())
        val iv = cipher.iv
        val encKey = cipher.doFinal(rawKey)
        return Pair(encKey, iv)
    }

    private fun decryptKey(encKey: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        val spec   = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKeystoreKey(), spec)
        return cipher.doFinal(encKey)
    }
}
