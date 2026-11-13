package com.minimart.pos.util

/**
 * Rules for the passphrase that locks an off-device backup. The file leaves the phone (WhatsApp, email, a flash
 * disk), so the passphrase is the only thing between a stranger and the shop's sales and customers; it also cannot be
 * recovered, so a typo when choosing it would make the backup useless. Hence: long enough, not trivial, typed twice.
 */
object BackupPassphrase {
    const val MIN_LENGTH = 8

    enum class Problem { TOO_SHORT, TOO_SIMPLE, MISMATCH }

    private val COMMON = setOf("password", "passw0rd", "12345678", "123456789", "1234567890", "qwertyui", "qwertyuiop",
        "minimart", "minimart1", "minimartpos", "11111111", "00000000", "abcd1234", "iloveyou", "asdfghjk")

    /** The first problem with [pass] / [again], or null when it can be used. */
    fun problem(pass: String, again: String): Problem? = when {
        pass.length < MIN_LENGTH -> Problem.TOO_SHORT
        pass.toSet().size < 3 || pass.lowercase() in COMMON || isRun(pass) -> Problem.TOO_SIMPLE
        pass != again -> Problem.MISMATCH
        else -> null
    }

    /** 12345678, abcdefgh, 87654321: every character one step from the last. */
    private fun isRun(s: String): Boolean {
        val d = s.zipWithNext { a, b -> b.code - a.code }
        return d.all { it == 1 } || d.all { it == -1 }
    }
}
