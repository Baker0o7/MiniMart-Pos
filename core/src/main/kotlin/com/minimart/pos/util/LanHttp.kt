package com.minimart.pos.util

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

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

    // ── Server side: reading a request and writing a reply, in bytes ──────────────────────────────────────────

    /** A request that cannot be served; [status] is the HTTP code to answer with. */
    class HttpError(val status: Int, message: String) : IOException(message)

    class Request(val method: String, val path: String, private val headers: Map<String, String>, val body: String) {
        /** Header lookup ignores case, as HTTP says it must. */
        fun header(name: String): String? = headers[name.lowercase()]
    }

    const val MAX_REQUEST_HEAD_BYTES = 16 * 1024
    private const val MAX_REQUEST_HEADERS = 64
    private val TOKEN = Regex("[!#$%&'*+.^_`|~0-9A-Za-z-]+")

    /**
     * Reads one request from [input] without trusting the sender. The request line and headers are limited in
     * size, every header is checked, and the body is read as exactly Content-Length BYTES and then decoded as strict
     * UTF-8: reading it as characters made the server count accented or emoji text wrongly, and a half-read body
     * looked like a bad signature. Throws [HttpError] with the status to answer.
     */
    fun readRequest(input: InputStream, maxBodyBytes: Int = MAX_BODY_BYTES): Request {
        var budget = MAX_REQUEST_HEAD_BYTES
        fun line(): String? {
            val bytes = ByteArrayOutputStream()
            while (true) {
                val b = input.read()
                if (b < 0) return if (bytes.size() == 0) null else bytes.toString(Charsets.ISO_8859_1.name())
                if (--budget < 0) throw HttpError(431, "Headers too large")
                if (b == '\n'.code) return bytes.toString(Charsets.ISO_8859_1.name()).removeSuffix("\r")
                bytes.write(b)
            }
        }

        val requestLine = line() ?: throw HttpError(400, "Empty request")
        val parts = requestLine.split(' ')
        if (parts.size != 3 || !parts[2].startsWith("HTTP/1.")) throw HttpError(400, "Bad request line")
        val method = parts[0]; val path = parts[1]
        if (!Regex("[A-Z]{3,8}").matches(method)) throw HttpError(400, "Bad method")
        if (!path.startsWith("/") || path.length > 2048 || path.any { it < ' ' || it == '\u007f' }) throw HttpError(400, "Bad path")

        val headers = HashMap<String, String>()
        while (true) {
            val h = line() ?: throw HttpError(400, "Headers cut short")
            if (h.isEmpty()) break
            if (headers.size >= MAX_REQUEST_HEADERS) throw HttpError(431, "Too many headers")
            val idx = h.indexOf(':')
            if (idx <= 0) throw HttpError(400, "Bad header")
            val name = h.substring(0, idx).lowercase()
            if (!TOKEN.matches(name)) throw HttpError(400, "Bad header name")
            val value = h.substring(idx + 1).trim()
            val old = headers[name]
            // Two different values for the same header is how request smuggling and signature tricks start.
            if (old != null && old != value) throw HttpError(400, "Conflicting header")
            headers[name] = value
        }
        if (headers.containsKey("transfer-encoding")) throw HttpError(501, "Chunked bodies are not supported")

        val lengthText = headers["content-length"]
        val length = when {
            lengthText == null -> 0
            !Regex("[0-9]{1,9}").matches(lengthText) -> throw HttpError(400, "Bad Content-Length")
            else -> lengthText.toInt()
        }
        if (length > maxBodyBytes) throw HttpError(413, "Payload too large")

        // Grow as data really arrives, so a peer that only claims a big body does not make the phone reserve it.
        val buffer = ByteArrayOutputStream(minOf(length, 8192))
        val chunk = ByteArray(8192)
        var left = length
        while (left > 0) {
            val n = input.read(chunk, 0, minOf(chunk.size, left))
            if (n < 0) throw HttpError(400, "Body cut short")
            buffer.write(chunk, 0, n)
            left -= n
        }
        val body = try {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(buffer.toByteArray())).toString()
        } catch (_: CharacterCodingException) { throw HttpError(400, "Body is not valid UTF-8") }
        return Request(method, path, headers, body)
    }

    private fun reason(code: Int) = when (code) {
        200 -> "OK"; 400 -> "Bad Request"; 401 -> "Unauthorized"; 404 -> "Not Found"; 413 -> "Payload Too Large"
        426 -> "Upgrade Required"; 429 -> "Too Many Requests"; 431 -> "Request Header Fields Too Large"
        500 -> "Internal Server Error"; 501 -> "Not Implemented"; else -> "Error"
    }

    /** A complete reply as bytes: CRLF line ends, UTF-8 body, and a Content-Length that is the real byte count. */
    fun buildResponse(code: Int, body: String, extraHeaders: List<Pair<String, String>> = emptyList()): ByteArray {
        require(extraHeaders.all { (k, v) -> k.none { it == '\r' || it == '\n' || it == ':' } && v.none { it == '\r' || it == '\n' } }) { "bad header" }
        val payload = body.toByteArray(Charsets.UTF_8)
        val head = StringBuilder()
        head.append("HTTP/1.1 ").append(code).append(' ').append(reason(code)).append("\r\n")
        head.append("Content-Type: application/json; charset=utf-8\r\n")
        extraHeaders.forEach { (k, v) -> head.append(k).append(": ").append(v).append("\r\n") }
        head.append("Content-Length: ").append(payload.size).append("\r\n")
        head.append("Connection: close\r\n\r\n")
        return head.toString().toByteArray(Charsets.ISO_8859_1) + payload
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
