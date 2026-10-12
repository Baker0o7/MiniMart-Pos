package com.minimart.pos.sync

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream

class LimitedInputStreamTest {
    @Test fun stopsAtTheLimit() {
        val s = LimitedInputStream(ByteArrayInputStream(ByteArray(1000) { 65 }), 100)
        assertEquals(100, s.readBytes().size)
    }
    @Test fun passesShortInputThrough() {
        val s = LimitedInputStream(ByteArrayInputStream("hello".toByteArray()), 100)
        assertEquals("hello", String(s.readBytes()))
    }
    @Test fun singleByteReadsAlsoCounted() {
        val s = LimitedInputStream(ByteArrayInputStream(ByteArray(10)), 3)
        var n = 0
        while (s.read() != -1) n++
        assertEquals(3, n)
    }
}
