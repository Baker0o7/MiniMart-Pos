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
        val digest = MessageDigest.getInstance("SHA-256")
        val computed = digest.digest(pin.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return computed == sha256Hash
    }

    private fun generateSalt(): ByteArray {
        val salt = ByteArray(16)
        java.security.SecureRandom().nextBytes(salt)
        return salt
    }
}
