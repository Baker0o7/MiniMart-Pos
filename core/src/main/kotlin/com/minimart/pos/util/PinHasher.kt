package com.minimart.pos.util

import com.lambdapioneer.argon2kt.Argon2Kt
import com.lambdapioneer.argon2kt.Argon2KtResult
import com.lambdapioneer.argon2kt.Argon2Mode
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PIN hashing using Argon2id (RFC 9106 recommended parameters for
 * interactive use: t=3, m=64MB, p=4).
 *
 * Legacy SHA-256 hashes are detected and accepted during login, then
 * transparently re-hashed to Argon2id on success.
 */
@Singleton
class PinHasher @Inject constructor() {

    companion object {
        // Argon2id parameters — OWASP recommended for interactive login
        private const val ITERATIONS   = 3
        private const val MEMORY_KB     = 64 * 1024   // 64 MB
        private const val PARALLELISM   = 4
        private const val HASH_LENGTH   = 32           // 256-bit output

        // Prefix to identify Argon2id hashes (Argon2Kt uses $argon2id$ format)
        private const val ARGON2_PREFIX = "\$argon2id\$"
        // Legacy SHA-256 hashes are 64 hex chars
        private const val SHA256_LENGTH = 64

        fun isArgon2Hash(hash: String) = hash.startsWith(ARGON2_PREFIX)
        fun isLegacySha256(hash: String) = hash.length == SHA256_LENGTH && !hash.startsWith("$")
    }

    private val argon2 = Argon2Kt()

    /** Hash a PIN using Argon2id — returns the full encoded string */
    fun hash(pin: String): String {
        val result: Argon2KtResult = argon2.hash(
            mode        = Argon2Mode.ARGON2_ID,
            password    = pin.toByteArray(Charsets.UTF_8),
            salt        = generateSalt(),
            tCostInIterations   = ITERATIONS,
            mCostInKibibyte     = MEMORY_KB,
            parallelism = PARALLELISM,
            hashLengthInBytes   = HASH_LENGTH
        )
        return result.encodedOutputAsString()
    }

    /** Verify a PIN against a stored hash (supports both Argon2id and legacy SHA-256) */
    fun verify(pin: String, storedHash: String): Boolean {
        return when {
            isArgon2Hash(storedHash) -> verifyArgon2(pin, storedHash)
            isLegacySha256(storedHash) -> verifyLegacy(pin, storedHash)
            else -> false
        }
    }

    /** True if the stored hash needs upgrading to Argon2id */
    fun needsUpgrade(storedHash: String) = !isArgon2Hash(storedHash)

    private fun verifyArgon2(pin: String, encoded: String): Boolean {
        return try {
            argon2.verify(
                mode     = Argon2Mode.ARGON2_ID,
                encoded  = encoded,
                password = pin.toByteArray(Charsets.UTF_8)
            )
        } catch (_: Exception) { false }
    }

    private fun verifyLegacy(pin: String, sha256Hash: String): Boolean {
        // Bug fix: `computed == sha256Hash` is a regular String comparison that returns
        // early on the first mismatched character — a timing side-channel that a
        // sufficiently determined attacker could use to distinguish "wrong PIN" from
        // "almost-right PIN" character by character. On a local retail device the
        // attack surface is limited, but it's trivially avoidable. MessageDigest.isEqual
        // does a constant-time length + byte comparison regardless of where the first
        // difference is, so the response time reveals nothing about the hash content.
        val digest = MessageDigest.getInstance("SHA-256")
        val computed = digest.digest(pin.toByteArray(Charsets.UTF_8))
        val stored   = sha256Hash.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        return MessageDigest.isEqual(computed, stored)
    }

    private fun generateSalt(): ByteArray {
        val salt = ByteArray(16)
        java.security.SecureRandom().nextBytes(salt)
        return salt
    }
}
