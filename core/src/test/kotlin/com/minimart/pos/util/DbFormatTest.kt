package com.minimart.pos.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class DbFormatTest {
    private val plain = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII) + ByteArray(100)

    @Test fun `plain sqlite header is recognised`() = assertEquals(DbFormat.Kind.PLAIN, DbFormat.classify(plain))
    @Test fun `random bytes count as encrypted`() = assertEquals(DbFormat.Kind.ENCRYPTED, DbFormat.classify(ByteArray(64) { (it * 7 + 3).toByte() }))
    @Test fun `empty or missing file`() {
        assertEquals(DbFormat.Kind.MISSING_OR_EMPTY, DbFormat.classify(null))
        assertEquals(DbFormat.Kind.MISSING_OR_EMPTY, DbFormat.classify(ByteArray(0)))
    }
    @Test fun `a short partial header is not plain`() =
        assertEquals(DbFormat.Kind.ENCRYPTED, DbFormat.classify("SQLite form".toByteArray()))
    @Test fun `raw key passphrase has the form SQLCipher expects`() {
        val p = DbFormat.rawKeyPassphrase(ByteArray(32) { it.toByte() })
        assertEquals("x'" + (0 until 32).joinToString("") { String.format("%02x", it) } + "'", p)
        assertEquals(68, p.length)
    }
    @Test fun `key must be 32 bytes`() { assertThrows(IllegalArgumentException::class.java) { DbFormat.rawKeyPassphrase(ByteArray(16)) } }
}
