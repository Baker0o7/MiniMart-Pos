package com.minimart.pos.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncCryptoTest {
    private val secret = "1234567890123456"
    private val aad = SyncCrypto.requestAad("POST", "/apply", "1800000000000")

    @Test fun roundTripsIncludingAccentsAndEmoji() {
        val text = """[{"name":"Café au lait ☕","qty":2}]"""
        assertEquals(text, SyncCrypto.open(secret, aad, SyncCrypto.seal(secret, aad, text)))
    }

    @Test fun theSealedFormShowsNothingOfThePlaintext() {
        val sealed = SyncCrypto.seal(secret, aad, "customer balance 4500")
        assertFalse(sealed.contains("customer"))
        assertFalse(sealed.contains("4500"))
    }

    @Test fun everyMessageUsesAFreshNonce() {
        assertNotEquals(SyncCrypto.seal(secret, aad, "same"), SyncCrypto.seal(secret, aad, "same"))
    }

    @Test fun aDifferentPairingCodeCannotOpenIt() {
        assertNull(SyncCrypto.open("0000000000000000", aad, SyncCrypto.seal(secret, aad, "x")))
    }

    @Test fun aMessageForOnePathCannotBeReplayedOnAnother() {
        val sealed = SyncCrypto.seal(secret, aad, "x")
        assertNull(SyncCrypto.open(secret, SyncCrypto.requestAad("POST", "/snapshot", "1800000000000"), sealed))
        assertNull(SyncCrypto.open(secret, SyncCrypto.requestAad("POST", "/apply", "1800000000001"), sealed))
    }

    @Test fun aRequestCannotBePassedOffAsAReply() {
        val sealed = SyncCrypto.seal(secret, aad, "x")
        assertNull(SyncCrypto.open(secret, SyncCrypto.responseAad("/apply", "1800000000000"), sealed))
    }

    @Test fun alteredOrTruncatedMessagesAreRejected() {
        val sealed = SyncCrypto.seal(secret, aad, "important")
        val flipped = (if (sealed[20] == 'A') 'B' else 'A').let { sealed.substring(0, 20) + it + sealed.substring(21) }
        assertNull(SyncCrypto.open(secret, aad, flipped))
        assertNull(SyncCrypto.open(secret, aad, sealed.substring(0, 10)))
        assertNull(SyncCrypto.open(secret, aad, ""))
        assertNull(SyncCrypto.open(secret, aad, "not base64 !!"))
    }

    @Test fun pairingCodesAreSixteenDigitsAndDiffer() {
        val a = PairingCode.generate(); val b = PairingCode.generate()
        assertEquals(16, a.length)
        assertTrue(a.all { it.isDigit() })
        assertNotEquals(a, b)
    }

    @Test fun codesAreGroupedInFoursForReading() {
        assertEquals("1234 5678 9012 3456", PairingCode.grouped("1234567890123456"))
    }
}
