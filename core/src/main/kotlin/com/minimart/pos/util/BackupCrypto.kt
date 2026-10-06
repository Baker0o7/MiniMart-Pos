package com.minimart.pos.util

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Passphrase-based encryption for backup files, so a backup that leaves the phone (shared,
 * cloud, USB) can't be read without the passphrase. Unlike a Keystore key, a passphrase works on
 * any device, so encrypted backups still restore on a new phone.
 *
 * File layout: "MMBK" + version(1) + salt(16) + iv(12) + AES-256-GCM ciphertext (with tag).
 * Key = PBKDF2-HMAC-SHA256(passphrase, salt, 210,000 iterations).
 */
object BackupCrypto {
    class WrongPassphraseException : Exception("Wrong passphrase or damaged backup")

    private val MAGIC = byteArrayOf('M'.code.toByte(), 'M'.code.toByte(), 'B'.code.toByte(), 'K'.code.toByte(), 1)
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val ITERATIONS = 210_000
    private const val TAG_BITS = 128

    fun isEncrypted(file: File): Boolean = try {
        file.inputStream().use { ins ->
            val h = ByteArray(MAGIC.size)
            ins.read(h) == h.size && h.contentEquals(MAGIC)
        }
    } catch (_: IOException) { false }

    private fun key(passphrase: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, ITERATIONS, 256)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    fun encrypt(input: InputStream, output: OutputStream, passphrase: String) {
        require(passphrase.isNotEmpty()) { "Passphrase must not be empty" }
        val rnd = SecureRandom()
        val salt = ByteArray(SALT_LEN).also { rnd.nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { rnd.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(passphrase, salt), GCMParameterSpec(TAG_BITS, iv))
        output.write(MAGIC); output.write(salt); output.write(iv)
        CipherOutputStream(output, cipher).use { out -> input.copyTo(out) }
    }

    /** @throws WrongPassphraseException if the passphrase is wrong or the file was tampered with. */
    fun decrypt(input: InputStream, output: OutputStream, passphrase: String) {
        val header = ByteArray(MAGIC.size)
        readFully(input, header)
        if (!header.contentEquals(MAGIC)) throw IllegalArgumentException("Not an encrypted MiniMart backup")
        val salt = ByteArray(SALT_LEN).also { readFully(input, it) }
        val iv = ByteArray(IV_LEN).also { readFully(input, it) }
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(passphrase, salt), GCMParameterSpec(TAG_BITS, iv))
            CipherInputStream(input, cipher).use { it.copyTo(output) }
        } catch (_: GeneralSecurityException) {
            throw WrongPassphraseException()
        } catch (_: IOException) {   // GCM tag failure surfaces as an IOException from CipherInputStream
            throw WrongPassphraseException()
        }
    }

    private fun readFully(input: InputStream, buf: ByteArray) {
        var off = 0
        while (off < buf.size) {
            val n = input.read(buf, off, buf.size - off)
            if (n < 0) throw IllegalArgumentException("Backup file is truncated")
            off += n
        }
    }
}
