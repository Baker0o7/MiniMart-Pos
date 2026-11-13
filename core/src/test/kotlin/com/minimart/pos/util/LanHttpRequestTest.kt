package com.minimart.pos.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

class LanHttpRequestTest {
    private fun raw(head: String, body: ByteArray = ByteArray(0)) = head.toByteArray(Charsets.ISO_8859_1) + body
    private fun post(body: String, extra: String = "") =
        raw("POST /apply HTTP/1.1\r\nHost: x\r\nContent-Length: ${body.toByteArray().size}\r\n$extra\r\n", body.toByteArray())

    private fun status(bytes: ByteArray, max: Int = 1000): Int =
        try { LanHttp.readRequest(ByteArrayInputStream(bytes), max); fail("should have been refused") }
        catch (e: LanHttp.HttpError) { e.status }

    /** A stream that hands over one byte per read, like a slow Wi-Fi link. */
    private class Drip(private val data: ByteArray) : InputStream() {
        private var i = 0
        override fun read(): Int = if (i < data.size) data[i++].toInt() and 0xff else -1
        override fun read(b: ByteArray, off: Int, len: Int): Int = if (len == 0) 0 else if (i >= data.size) -1 else { b[off] = data[i++]; 1 }
    }

    @Test fun `body with accents and emoji is read by bytes not characters`() {
        val text = "{\"name\":\"Café مشروب ☕ 🍵\"}"
        assertTrue(text.toByteArray().size > text.length)
        val r = LanHttp.readRequest(ByteArrayInputStream(post(text)))
        assertEquals(text, r.body)
        assertEquals("POST", r.method); assertEquals("/apply", r.path)
    }

    @Test fun `a body that arrives one byte at a time is still complete`() {
        val text = "{\"x\":\"مرحبا 🍵\"}"
        assertEquals(text, LanHttp.readRequest(Drip(post(text))).body)
    }

    @Test fun `header names are found whatever their case`() {
        val r = LanHttp.readRequest(ByteArrayInputStream(post("{}", "x-sync-ts: 123\r\nX-SYNC-SIG: abc\r\n")))
        assertEquals("123", r.header("X-Sync-Ts")); assertEquals("abc", r.header("x-sync-sig"))
    }

    @Test fun `plain line feeds are accepted and a request with no body works`() {
        val r = LanHttp.readRequest(ByteArrayInputStream("GET /ping HTTP/1.1\nHost: x\n\n".toByteArray()))
        assertEquals("", r.body); assertEquals("/ping", r.path); assertNull(r.header("content-length"))
    }

    @Test fun `a body that stops early is refused`() {
        assertEquals(400, status(raw("POST /apply HTTP/1.1\r\nContent-Length: 50\r\n\r\n", "short".toByteArray())))
    }

    @Test fun `a claimed size over the limit is refused before reading it`() {
        assertEquals(413, status(raw("POST /apply HTTP/1.1\r\nContent-Length: 5000\r\n\r\n"), max = 1000))
    }

    @Test fun `bad or conflicting lengths are refused`() {
        assertEquals(400, status(raw("POST /a HTTP/1.1\r\nContent-Length: -1\r\n\r\n")))
        assertEquals(400, status(raw("POST /a HTTP/1.1\r\nContent-Length: 1e3\r\n\r\n")))
        assertEquals(400, status(raw("POST /a HTTP/1.1\r\nContent-Length: 2\r\nContent-Length: 3\r\n\r\nab")))
        assertEquals(501, status(raw("POST /a HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\n")))
    }

    @Test fun `an endless header line is stopped`() {
        assertEquals(431, status(raw("GET /ping HTTP/1.1\r\nX: " + "a".repeat(40_000) + "\r\n\r\n")))
    }

    @Test fun `too many headers are refused`() {
        val many = (1..100).joinToString("") { "H$it: v\r\n" }
        assertEquals(431, status(raw("GET /ping HTTP/1.1\r\n$many\r\n")))
    }

    @Test fun `bytes that are not valid text are refused`() {
        val bad = byteArrayOf(0x7b, 0xC3.toByte(), 0x28, 0x7d) // { <broken two-byte sequence> ( }
        assertEquals(400, status(raw("POST /apply HTTP/1.1\r\nContent-Length: ${bad.size}\r\n\r\n", bad)))
    }

    @Test fun `malformed request lines and headers are refused`() {
        assertEquals(400, status(raw("")))
        assertEquals(400, status(raw("GET\r\n\r\n")))
        assertEquals(400, status(raw("GET ping HTTP/1.1\r\n\r\n")))
        assertEquals(400, status(raw("get /ping HTTP/1.1\r\n\r\n")))
        assertEquals(400, status(raw("GET /ping HTTP/1.1\r\nno colon here\r\n\r\n")))
        assertEquals(400, status(raw("GET /ping HTTP/1.1\r\nHost: x\r\n"))) // never finished
    }

    @Test fun `a reply carries the real byte count and parses back`() {
        val body = "{\"msg\":\"اكتمل ✓ 🍵\"}"
        val bytes = LanHttp.buildResponse(429, body, listOf("X-Sync-Enc" to "1"))
        val text = String(bytes, Charsets.ISO_8859_1)
        assertTrue(text.startsWith("HTTP/1.1 429 Too Many Requests\r\n"))
        assertTrue(text.contains("\r\nContent-Length: ${body.toByteArray().size}\r\n"))
        assertTrue(text.contains("\r\n\r\n"))
        val parsed = LanHttp.readResponse(ByteArrayInputStream(bytes))
        assertEquals(429, parsed.code); assertEquals(body, parsed.body); assertEquals("1", parsed.header("x-sync-enc"))
    }

    @Test fun `a header with a line break in it cannot be written`() {
        try { LanHttp.buildResponse(200, "{}", listOf("X" to "a\r\nInjected: 1")); fail("should refuse") } catch (_: IllegalArgumentException) {}
    }
}
