package com.minimart.pos.util

import java.security.GeneralSecurityException
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Authenticated encryption (AES-256-GCM) for small secrets. The key is supplied by the caller, in the
 * app the Android Keystore, so the key itself never sits in a file. [aad] (for example the preference
 * name) is authenticated but not stored, so a sealed value copied under another name will not open.
 * The IV is chosen by the cipher, as the Keystore requires, and stored in front of the ciphertext.
 */
class AesGcmBox(private val key: SecretKey) {
    fun seal(aad: String, plain: ByteArray): String {
        val c = Cipher.getInstance(TRANSFORM)
        c.init(Cipher.ENCRYPT_MODE, key)
        c.updateAAD(aad.toByteArray(Charsets.UTF_8))
        val ct = c.doFinal(plain)
        return Base64.getEncoder().encodeToString(byteArrayOf(VERSION) + c.iv + ct)
    }

    /** The plain bytes, or null when the value was altered, belongs to another name or another key. */
    fun open(aad: String, sealed: String): ByteArray? = try {
        val raw = Base64.getDecoder().decode(sealed)
        if (raw.size < 1 + IV_BYTES + TAG_BYTES || raw[0] != VERSION) null
        else {
            val c = Cipher.getInstance(TRANSFORM)
            c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BYTES * 8, raw, 1, IV_BYTES))
            c.updateAAD(aad.toByteArray(Charsets.UTF_8))
            c.doFinal(raw, 1 + IV_BYTES, raw.size - 1 - IV_BYTES)
        }
    } catch (_: GeneralSecurityException) { null } catch (_: IllegalArgumentException) { null }

    private companion object {
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val VERSION: Byte = 1
        const val IV_BYTES = 12
        const val TAG_BYTES = 16
    }
}
