package com.minimart.pos.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import javax.crypto.KeyGenerator

class FieldCipherTest {
    private fun key() = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val cipher = FieldCipher(AesGcmBox(key()))

    @Test fun textComesBackExactlyIncludingNonAsciiAndNewlines() {
        val sms = "QWE123ABCD Confirmed. Ksh500.00 received from JOHN OTIENO 0712345678\nBalance Ksh1,234.50 ✓"
        assertEquals(sms, cipher.reveal("sms", cipher.protect("sms", sms)))
    }

    @Test fun theStoredFormHoldsNoReadableText() {
        val stored = cipher.protect("phone", "0712345678")
        assertTrue(stored.startsWith(FieldCipher.PREFIX))
        assertFalse(stored.contains("0712345678"))
    }

    @Test fun sealingTheSameTextTwiceGivesDifferentStoredValues() {
        assertNotEquals(cipher.protect("phone", "0712345678"), cipher.protect("phone", "0712345678"))
    }

    @Test fun blankTextIsLeftAloneSoNoPhoneStaysVisible() {
        assertEquals("", cipher.protect("phone", ""))
        assertEquals("  ", cipher.protect("phone", "  "))
        assertFalse(cipher.needsSealing(""))
    }

    @Test fun textFromBeforeEncryptionIsReadAsItIsAndMarkedForSealing() {
        assertEquals("0711000111", cipher.reveal("phone", "0711000111"))
        assertTrue(cipher.needsSealing("0711000111"))
        assertFalse(cipher.needsSealing(cipher.protect("phone", "0711000111")))
    }

    @Test fun sealingTwiceDoesNotDoubleSeal() {
        val once = cipher.protect("phone", "0712345678")
        assertEquals(once, cipher.protect("phone", once))
    }

    @Test fun aValueMovedToAnotherFieldCannotBeRead() {
        assertEquals("", cipher.reveal("name", cipher.protect("phone", "0712345678")))
    }

    @Test fun anotherPhonesKeyCannotReadItAndAlteredTextReadsAsEmpty() {
        val stored = cipher.protect("phone", "0712345678")
        assertEquals("", FieldCipher(AesGcmBox(key())).reveal("phone", stored))
        val altered = stored.dropLast(3) + (if (stored.endsWith("AAA")) "BBB" else "AAA")
        assertEquals("", cipher.reveal("phone", altered))
    }

    @Test fun withoutAKeyTextIsStoredAsItIsAndSealedTextIsUnreadable() {
        assertEquals("0712345678", FieldCipher.NONE.protect("phone", "0712345678"))
        assertFalse(FieldCipher.NONE.needsSealing("0712345678"))
        assertEquals("", FieldCipher.NONE.reveal("phone", cipher.protect("phone", "0712345678")))
    }
}
