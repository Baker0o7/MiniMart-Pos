package com.minimart.pos.util

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket

/**
 * A minimal HTTP/1.1 client for talking to another till on the shop's own Wi-Fi.
 *
 * It exists so the app can forbid cleartext HTTP system-wide (see network_security_config.xml):
 * Android's policy applies to the platform HTTP stack, and cannot name a private IP range. Sync
 * bodies are AES-GCM encrypted and signed before they get here, so this carries no readable data.
 * Internet traffic (M-Pesa, cloud report) keeps using HTTPS through the normal stack.
 */
object LanHttp {
    class Response(val code: Int, val body: String, private val headers: Map<String, String>) {
        fun header(name: String): String? = headers[name.lowercase()]
    }

    private const val MAX_HEADER_BYTES = 64 * 1024
    const val MAX_BODY_BYTES = 10_000_000

    /** Throws [IOException] when the peer cannot be reached or sends something that is not HTTP. */
    fun request(
        host: String, port: Int, method: String, path: String,
        headers: Map<String, String> = emptyMap(), body: String? = null,
        connectTimeoutMs: Int = 8000, readTimeoutMs: Int = 32000
    ): Response {
        require(path.startsWith("/") && path.none { it == '\r' || it == '\n' || it == ' ' }) { "bad path" }
        require(headers.all { (k, v) -> k.none { it == '\r' || it == '\n' || it == ':' } && v.none { it == '\r' || it == '\n' } }) { "bad header" }
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), connectTimeoutMs)
            socket.soTimeout = readTimeoutMs
            val payload = body?.toByteArray(Charsets.UTF_8)
            val head = StringBuilder()
            head.append(method).append(' ').append(path).append(" HTTP/1.1\r\n")
            head.append("Host: ").append(host).append(':').append(port).append("\r\n")
            head.append("Connection: close\r\n")
            headers.forEach { (k, v) -> head.append(k).append(": ").append(v).append("\r\n") }
            if (payload != null) head.append("Content-Length: ").append(payload.size).append("\r\n")
            head.append("\r\n")
            val out = socket.getOutputStream()
            out.write(head.toString().toByteArray(Charsets.ISO_8859_1))
            if (payload != null) out.write(payload)
            out.flush()
            return readResponse(BufferedInputStream(socket.getInputStream()))
        }
    }

    internal fun readResponse(input: InputStream): Response {
        val status = readLine(input, MAX_HEADER_BYTES) ?: throw IOException("No reply")
        val parts = status.split(' ', limit = 3)
        if (parts.size < 2 || !parts[0].startsWith("HTTP/")) throw IOException("Not an HTTP reply")
        val code = parts[1].toIntOrNull() ?: throw IOException("Bad status line")

        val headers = HashMap<String, String>()
        var consumed = status.length
        while (true) {
            val line = readLine(input, MAX_HEADER_BYTES) ?: break
            if (line.isEmpty()) break
            consumed += line.length
            if (consumed > MAX_HEADER_BYTES) throw IOException("Headers too large")
            val idx = line.indexOf(':')
            if (idx > 0) headers[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
        }

        val length = headers["content-length"]?.toIntOrNull()
        val buffer = ByteArrayOutputStream()
        if (length != null) {
            if (length < 0 || length > MAX_BODY_BYTES) throw IOException("Reply too large")
            val chunk = ByteArray(8192)
            var left = length
            while (left > 0) {
                val n = input.read(chunk, 0, minOf(chunk.size, left))
                if (n < 0) break
                buffer.write(chunk, 0, n)
                left -= n
            }
        } else {
            val chunk = ByteArray(8192)
            while (true) {
                val n = input.read(chunk)
                if (n < 0) break
                buffer.write(chunk, 0, n)
                if (buffer.size() > MAX_BODY_BYTES) throw IOException("Reply too large")
            }
        }
        return Response(code, buffer.toString(Charsets.UTF_8.name()), headers)
    }

    /** One line, ending at LF (a CR before it is dropped); null at end of stream with nothing read. */
    private fun readLine(input: InputStream, max: Int): String? {
        val sb = StringBuilder()
        while (true) {
            val b = input.read()
            if (b < 0) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) return sb.toString().removeSuffix("\r")
            sb.append(b.toChar())
            if (sb.length > max) throw IOException("Line too long")
        }
    }
}
