package com.minimart.pos.util

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

class DarajaRulesTest {
    @AfterEach fun reset() { Lang.arabic = false }

    @Test fun `http codes are sorted into what to do`() {
        assertEquals(DarajaRules.HttpKind.OK, DarajaRules.httpKind(200))
        assertEquals(DarajaRules.HttpKind.AUTH_EXPIRED, DarajaRules.httpKind(401))
        assertEquals(DarajaRules.HttpKind.RATE_LIMITED, DarajaRules.httpKind(429))
        listOf(500, 502, 503, 504).forEach { assertEquals(DarajaRules.HttpKind.SERVER_BUSY, DarajaRules.httpKind(it)) }
        assertEquals(DarajaRules.HttpKind.REJECTED, DarajaRules.httpKind(400))
    }

    @Test fun `a push is only retried when it cannot have left the phone`() {
        assertTrue(DarajaRules.isSafeToRetryPush(UnknownHostException("no dns")))
        assertTrue(DarajaRules.isSafeToRetryPush(ConnectException("refused")))
        assertTrue(DarajaRules.isSafeToRetryPush(IOException("wrapped", ConnectException())))
        assertTrue(DarajaRules.isSafeToRetryPush(SocketTimeoutException("connect timed out")))
        assertFalse(DarajaRules.isSafeToRetryPush(SocketTimeoutException("Read timed out")))
        assertFalse(DarajaRules.isSafeToRetryPush(SSLException("reset after sending")))
        assertFalse(DarajaRules.isSafeToRetryPush(IllegalStateException("bug")))
    }

    @Test fun `a lost answer after sending is reported as unknown, never as plain failure`() {
        assertTrue(DarajaRules.pushOutcomeUnknown(SocketTimeoutException("Read timed out")))
        assertTrue(DarajaRules.pushOutcomeUnknown(SSLException("connection reset")))
        assertFalse(DarajaRules.pushOutcomeUnknown(UnknownHostException("no dns")))
        assertFalse(DarajaRules.pushOutcomeUnknown(ConnectException("refused")))
        assertFalse(DarajaRules.pushOutcomeUnknown(IllegalStateException("bug")))
    }

    @Test fun `no network error is both safe to retry and unknown`() {
        val all = listOf(UnknownHostException(), ConnectException(), SocketTimeoutException("Read timed out"), SSLException("x"), IOException("y"))
        all.forEach { assertFalse(DarajaRules.isSafeToRetryPush(it) && DarajaRules.pushOutcomeUnknown(it), it.toString()) }
    }

    @Test fun `polling slows down while checks fail and is capped`() {
        assertEquals(3_500L, DarajaRules.pollDelayMs(0))
        val delays = (0..10).map { DarajaRules.pollDelayMs(it) }
        assertEquals(delays.sorted(), delays)
        assertEquals(10_000L, delays.last())
    }

    @Test fun `retry delays grow and jitter stays within thirty percent`() {
        assertEquals(500L, DarajaRules.retryDelayMs(1))
        assertTrue(DarajaRules.retryDelayMs(2) > DarajaRules.retryDelayMs(1))
        assertEquals(650L, DarajaRules.retryDelayMs(1, 1.0))
        assertEquals(500L, DarajaRules.retryDelayMs(1, -5.0))
        assertEquals(650L, DarajaRules.retryDelayMs(1, 99.0))
    }

    @Test fun `known result codes get plain words in both languages`() {
        listOf("1", "1032", "1037", "2001").forEach { code ->
            Lang.arabic = false
            val en = DarajaRules.resultMessage(code, "ignored")
            Lang.arabic = true
            val ar = DarajaRules.resultMessage(code, "ignored")
            assertTrue(en.none { it in '؀'..'ۿ' } && !en.contains("ignored"), en)
            assertTrue(ar.any { it in '؀'..'ۿ' }, ar)
        }
    }

    @Test fun `an unknown code uses Safaricom's text, tidied, or a generic line`() {
        assertEquals("Rule failed", DarajaRules.resultMessage("9999", "Rule\r\n failed"))
        assertTrue(DarajaRules.resultMessage("9999", "").contains("9999"))
        assertTrue(DarajaRules.resultMessage("9999", "x".repeat(500)).length <= 120)
    }

    @Test fun `still processing codes are recognised`() {
        assertTrue(DarajaRules.isStillProcessing("500.001.1001"))
        assertFalse(DarajaRules.isStillProcessing("400.002.02"))
    }

    @Test fun `unknown outcome message warns against sending again`() {
        assertTrue(DarajaRules.unknownOutcomeMessage().contains("before sending again"))
        Lang.arabic = true
        assertTrue(DarajaRules.unknownOutcomeMessage().any { it in '؀'..'ۿ' })
    }
}
