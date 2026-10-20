package com.minimart.pos.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.ServerSocket
import kotlin.concurrent.thread

class LanHttpTest {
    /** Runs a one-shot server that records the raw request and answers with [reply]. */
    private fun serve(reply: ByteArray, captured: StringBuilder? = null, block: (Int) -> Unit) {
        ServerSocket(0).use { server ->
            val t = thread {
                server.accept().use { s ->
                    val ins = s.getInputStream()
                    val buf = ByteArray(65536)
                    // read until the request has arrived (headers + declared body)
                    var total = 0
                    var text = ""
                    while (true) {
                        val n = ins.read(buf, total, buf.size - total)
                        if (n < 0) break
                        total += n
                        text = String(buf, 0, total, Charsets.UTF_8)
                        val end = text.indexOf("\r\n\r\n")
                        if (end >= 0) {
                            val len = Regex("Content-Length: (\\d+)").find(text)?.groupValues?.get(1)?.toInt() ?: 0
                            if (text.substring(end + 4).toByteArray(Charsets.UTF_8).size >= len) break
                        }
                    }
                    captured?.append(text)
                    s.getOutputStream().write(reply)
                    s.getOutputStream().flush()
                }
            }
            block(server.localPort)
            t.join(5000)
        }
    }

    private fun http(code: String, headers: List<String>, body: String, lineEnd: String = "\n"): ByteArray =
        ("HTTP/1.1 $code" + lineEnd + headers.joinToString("") { it + lineEnd } + lineEnd + body).toByteArray(Charsets.UTF_8)

    @Test fun postSendsHeadersAndBodyAndParsesLfOnlyReply() {
        val seen = StringBuilder()
        val body = """{"name":"Café ☕"}"""
        val reply = http("200 OK", listOf("Content-Type: application/json", "X-Sync-Resp-Sig: abc", "Content-Length: 6"), "hello!\n")
        serve(reply, seen) { port ->
            val r = LanHttp.request("127.0.0.1", port, "POST", "/apply", mapOf("X-Sync-Ts" to "42"), body)
            assertEquals(200, r.code)
            assertEquals("hello!", r.body)
            assertEquals("abc", r.header("x-sync-resp-sig"))
            assertEquals("abc", r.header("X-Sync-Resp-Sig"))
        }
        val text = seen.toString()
        assertTrue(text.startsWith("POST /apply HTTP/1.1\r\n"))
        assertTrue(text.contains("X-Sync-Ts: 42\r\n"))
        assertTrue(text.contains("Content-Length: ${body.toByteArray(Charsets.UTF_8).size}\r\n"))
        assertTrue(text.endsWith(body))
    }

    @Test fun errorStatusStillReturnsItsBody() {
        serve(http("401 Error", listOf("Content-Length: 5"), "nope!")) { port ->
            val r = LanHttp.request("127.0.0.1", port, "GET", "/snapshot")
            assertEquals(401, r.code)
            assertEquals("nope!", r.body)
        }
    }

    @Test fun multiByteBodyIsReadByByteLength() {
        val text = "ünï☕"
        val len = text.toByteArray(Charsets.UTF_8).size
        serve(http("200 OK", listOf("Content-Length: $len"), text + "\n", "\r\n")) { port ->
            assertEquals(text, LanHttp.request("127.0.0.1", port, "GET", "/x").body)
        }
    }

    @Test fun replyWithoutLengthIsReadToEnd() {
        serve(http("200 OK", emptyList(), "until close")) { port ->
            assertEquals("until close", LanHttp.request("127.0.0.1", port, "GET", "/x").body)
        }
    }

    @Test fun nonHttpReplyIsRejected() {
        try {
            LanHttp.readResponse(ByteArrayInputStream("SSH-2.0-whatever\r\n".toByteArray()))
            fail("must not accept a non-HTTP reply")
        } catch (_: IOException) { }
    }

    @Test fun oversizedDeclaredBodyIsRejected() {
        try {
            LanHttp.readResponse(ByteArrayInputStream("HTTP/1.1 200 OK\r\nContent-Length: 999999999\r\n\r\n".toByteArray()))
            fail("must refuse a huge reply")
        } catch (_: IOException) { }
    }

    @Test fun unreachablePeerThrows() {
        val port = ServerSocket(0).use { it.localPort } // closed again: nothing listens here
        try {
            LanHttp.request("127.0.0.1", port, "GET", "/ping", connectTimeoutMs = 500)
            fail("expected an IOException")
        } catch (_: IOException) { }
    }

    @Test fun headerInjectionIsRefused() {
        try { LanHttp.request("127.0.0.1", 1, "GET", "/x", mapOf("A" to "b\r\nX: y")); fail() } catch (_: IllegalArgumentException) { }
        try { LanHttp.request("127.0.0.1", 1, "GET", "/x y"); fail() } catch (_: IllegalArgumentException) { }
        assertNull(null)
    }
}
