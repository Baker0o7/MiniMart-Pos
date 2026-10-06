package com.minimart.pos.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class BackupCryptoTest {
    private val data = ByteArray(100_000) { (it * 31).toByte() }

    private fun enc(pass: String): ByteArray =
        ByteArrayOutputStream().also { BackupCrypto.encrypt(ByteArrayInputStream(data), it, pass) }.toByteArray()

    private fun dec(blob: ByteArray, pass: String): ByteArray =
        ByteArrayOutputStream().also { BackupCrypto.decrypt(ByteArrayInputStream(blob), it, pass) }.toByteArray()

    @Test
    fun `round trip restores the original bytes`() {
        assertArrayEquals(data, dec(enc("correct horse"), "correct horse"))
    }

    @Test
    fun `ciphertext does not contain the plaintext and differs each time`() {
        val a = enc("pw1234"); val b = enc("pw1234")
        assertFalse(a.contentEquals(b))                       // random salt + IV
        assertFalse(String(a, Charsets.ISO_8859_1).contains(String(data.copyOf(64), Charsets.ISO_8859_1)))
    }

    @Test
    fun `wrong passphrase is rejected`() {
        val blob = enc("right-pass")
        try { dec(blob, "wrong-pass"); fail("expected WrongPassphraseException") }
        catch (_: BackupCrypto.WrongPassphraseException) { /* expected */ }
    }

    @Test
    fun `tampered ciphertext is rejected`() {
        val blob = enc("right-pass")
        blob[blob.size / 2] = (blob[blob.size / 2].toInt() xor 0x01).toByte()
        try { dec(blob, "right-pass"); fail("expected WrongPassphraseException") }
        catch (_: BackupCrypto.WrongPassphraseException) { /* expected */ }
    }

    @Test
    fun `non-backup data is rejected`() {
        try { dec("plain sqlite text".toByteArray(), "x"); fail("expected IllegalArgumentException") }
        catch (_: IllegalArgumentException) { /* expected */ }
    }

    @Test
    fun `isEncrypted recognises the header`() {
        val f = java.io.File.createTempFile("bk", ".mmbak")
        try {
            f.writeBytes(enc("abcdef"))
            assertTrue(BackupCrypto.isEncrypted(f))
            f.writeBytes("SQLite format 3\u0000".toByteArray())
            assertFalse(BackupCrypto.isEncrypted(f))
        } finally { f.delete() }
    }
}
