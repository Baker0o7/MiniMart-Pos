package com.minimart.pos.util

import org.junit.Assert.*
import org.junit.Test
import javax.crypto.KeyGenerator

class AesGcmBoxTest {
    private fun newKey() = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val box = AesGcmBox(newKey())

    @Test fun roundTrip() {
        val sealed = box.seal("name", "secret ✓".toByteArray())
        assertEquals("secret ✓", String(box.open("name", sealed)!!))
    }
    @Test fun emptyValueRoundTrips() = assertEquals(0, box.open("a", box.seal("a", ByteArray(0)))!!.size)
    @Test fun sameValueSealsDifferentlyEachTime() =
        assertNotEquals(box.seal("a", "x".toByteArray()), box.seal("a", "x".toByteArray()))
    @Test fun sealedTextDoesNotContainThePlainText() = assertFalse(box.seal("a", "hunter2hunter2".toByteArray()).contains("hunter2"))
    @Test fun wrongNameDoesNotOpen() = assertNull(box.open("b", box.seal("a", "x".toByteArray())))
    @Test fun wrongKeyDoesNotOpen() = assertNull(AesGcmBox(newKey()).open("a", box.seal("a", "x".toByteArray())))
    @Test fun alteredValueDoesNotOpen() {
        val raw = java.util.Base64.getDecoder().decode(box.seal("a", "hello".toByteArray()))
        raw[raw.size - 1] = (raw[raw.size - 1].toInt() xor 1).toByte()
        assertNull(box.open("a", java.util.Base64.getEncoder().encodeToString(raw)))
    }
    @Test fun garbageDoesNotOpenOrThrow() {
        listOf("", "!!!", "AAAA", java.util.Base64.getEncoder().encodeToString(ByteArray(40))).forEach { assertNull(it, box.open("a", it)) }
    }
}
