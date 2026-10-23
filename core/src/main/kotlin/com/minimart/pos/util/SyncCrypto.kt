package com.minimart.pos.util

import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Confidentiality for LAN sync. The two phones share a pairing code; every request and reply body is
 * sealed with AES-256-GCM under a key derived from it, so someone sharing the Wi-Fi sees only random bytes.
 * Each message is bound (as GCM "associated data") to its direction, path and timestamp, so a captured
 * message cannot be replayed against a different endpoint or passed off as a reply.
 */
object SyncCrypto {
    private const val ITERATIONS = 120_000
    private const val NONCE_BYTES = 12
    private const val TAG_BITS = 128
    private val SALT = "minimart-pos/lan-sync/v2".toByteArray(Charsets.UTF_8)

    private val keys = ConcurrentHashMap<String, SecretKeySpec>()
    private val random = SecureRandom()

    private fun key(secret: String): SecretKeySpec = keys.getOrPut(secret) {
        val spec = PBEKeySpec(secret.toCharArray(), SALT, ITERATIONS, 256)
        SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
    }

    fun requestAad(method: String, path: String, timestamp: String) = "req\n$method\n$path\n$timestamp"
    fun responseAad(path: String, requestTimestamp: String) = "resp\n$path\n$requestTimestamp"

    /** Returns Base64(nonce ‖ ciphertext ‖ tag). */
    fun seal(secret: String, aad: String, plaintext: String): String {
        val nonce = ByteArray(NONCE_BYTES).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(secret), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(aad.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(nonce + cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8)))
    }

    /** Returns the plaintext, or null if the message was altered, is for another endpoint, or the code differs. */
    fun open(secret: String, aad: String, sealed: String): String? = try {
        val raw = Base64.getDecoder().decode(sealed.trim())
        if (raw.size <= NONCE_BYTES) null else {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(secret), GCMParameterSpec(TAG_BITS, raw.copyOfRange(0, NONCE_BYTES)))
            cipher.updateAAD(aad.toByteArray(Charsets.UTF_8))
            String(cipher.doFinal(raw, NONCE_BYTES, raw.size - NONCE_BYTES), Charsets.UTF_8)
        }
    } catch (_: Exception) { null }
}

/** Pairing codes: 16 random digits (about 53 bits), from a cryptographically secure generator. */
object PairingCode {
    const val LENGTH = 16

    /** Draws again in the rare case a random code looks guessable, so a generated code is always [isStrong]. */
    fun generate(random: SecureRandom = SecureRandom()): String {
        while (true) {
            val code = buildString { repeat(LENGTH) { append(random.nextInt(10)) } }
            if (isStrong(code)) return code
        }
    }

    /**
     * True only for a code the app could have generated or a person could safely type: exactly
     * [LENGTH] digits, at least 6 different ones, and not a simple run like 0123456789012345.
     * Enforced in code so a short or guessable code can never be stored as the sync secret.
     */
    fun isStrong(code: String): Boolean {
        if (code.length != LENGTH || !code.all { it in '0'..'9' }) return false
        if (code.toSet().size < 6) return false
        val diffs = code.zipWithNext { a, b -> (b - a + 10) % 10 }.toSet()
        return !(diffs.size == 1 && (diffs.first() == 1 || diffs.first() == 9 || diffs.first() == 0))
    }

    /** Strips spaces and dashes people type between groups. */
    fun normalize(input: String): String = input.filter { it.isDigit() }

    /** "1234567890123456" -> "1234 5678 9012 3456" for reading aloud or off a screen. */
    fun grouped(code: String): String = code.chunked(4).joinToString(" ")
}
