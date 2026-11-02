package com.minimart.pos.util

/**
 * What a database file on disk is, judged from its first bytes. A plain SQLite file always starts with
 * "SQLite format 3" followed by a zero byte; an encrypted (SQLCipher) file starts with random-looking bytes.
 * Used to decide whether the shop's database still needs converting, so the decision can be tested without a phone.
 */
object DbFormat {
    enum class Kind { MISSING_OR_EMPTY, PLAIN, ENCRYPTED }

    private val MAGIC = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

    fun classify(header: ByteArray?): Kind = when {
        header == null || header.isEmpty() -> Kind.MISSING_OR_EMPTY
        header.size >= MAGIC.size && MAGIC.indices.all { header[it] == MAGIC[it] } -> Kind.PLAIN
        else -> Kind.ENCRYPTED      // anything else, including a damaged file: only opening it with the key can tell
    }

    /** The passphrase SQLCipher takes for a raw 256-bit key: skips its slow password-based key derivation on every open. */
    fun rawKeyPassphrase(key: ByteArray): String {
        require(key.size == 32) { "key must be 32 bytes" }
        return "x'" + key.joinToString("") { String.format(java.util.Locale.ROOT, "%02x", it) } + "'"
    }
}
