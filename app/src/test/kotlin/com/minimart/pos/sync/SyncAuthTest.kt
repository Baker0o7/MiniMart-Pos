package com.minimart.pos.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File

class SyncAuthTest {
    private val secret = "4821907356184027"
    private val now = 1_800_000_000_000L

    @Before @After fun freshCache() = SyncAuth.resetCacheForTest()

    private fun sig(body: String, ts: Long = now, method: String = "POST", path: String = "/apply") =
        SyncAuth.sign(secret, method, path, body, ts)

    @Test fun validSignatureAccepted() {
        assertTrue(SyncAuth.verify(secret, "POST", "/apply", "[1]", now.toString(), sig("[1]"), now))
    }

    @Test fun replayRejected() {
        val s = sig("[2]", method = "POST")
        assertTrue(SyncAuth.verify(secret, "POST", "/apply", "[2]", now.toString(), s, now))
        assertFalse(SyncAuth.verify(secret, "POST", "/apply", "[2]", now.toString(), s, now + 1))
    }

    @Test fun tamperedBodyRejected() {
        assertFalse(SyncAuth.verify(secret, "POST", "/apply", "[3,evil]", now.toString(), sig("[3]"), now))
    }

    @Test fun wrongSecretOrPathRejected() {
        assertFalse(SyncAuth.verify("0000000000000001", "POST", "/apply", "[4]", now.toString(), sig("[4]"), now))
        assertFalse(SyncAuth.verify(secret, "GET", "/snapshot", "[5]", now.toString(), sig("[5]"), now))
    }

    @Test fun staleTimestampRejected() {
        val old = now - SyncAuth.MAX_SKEW_MS - 1
        assertFalse(SyncAuth.verify(secret, "POST", "/apply", "[6]", old.toString(), sig("[6]", old), now))
    }

    @Test fun missingHeadersRejected() {
        assertFalse(SyncAuth.verify(secret, "POST", "/apply", "", null, null, now))
    }

    @Test fun signedResponseVerifies() {
        val sig = SyncAuth.signResponse(secret, "/snapshot", "1700000000000", "{\"products\":[]}")
        assertTrue(SyncAuth.verifyResponse(secret, "/snapshot", "1700000000000", "{\"products\":[]}", sig))
    }

    @Test fun alteredOrUnsignedResponseRejected() {
        val sig = SyncAuth.signResponse(secret, "/snapshot", "1700000000000", "{\"a\":1}")
        assertFalse(SyncAuth.verifyResponse(secret, "/snapshot", "1700000000000", "{\"a\":2}", sig))      // body changed
        assertFalse(SyncAuth.verifyResponse("0000000000000001", "/snapshot", "1700000000000", "{\"a\":1}", sig))    // wrong key
        assertFalse(SyncAuth.verifyResponse(secret, "/apply", "1700000000000", "{\"a\":1}", sig))         // other path
        assertFalse(SyncAuth.verifyResponse(secret, "/snapshot", "1700000000001", "{\"a\":1}", sig))      // other request
        assertFalse(SyncAuth.verifyResponse(secret, "/snapshot", "1700000000000", "{\"a\":1}", null))
        assertFalse(SyncAuth.verifyResponse(secret, "/snapshot", "1700000000000", "{\"a\":1}", ""))
    }

    @Test fun futureTimestampBeyondSkewRejected() {
        val future = now + SyncAuth.MAX_SKEW_MS + 1
        assertFalse(SyncAuth.verify(secret, "POST", "/apply", "[7]", future.toString(), sig("[7]", future), now))
    }

    @Test fun timestampAtTheEdgeOfTheWindowIsAccepted() {
        val edge = now - SyncAuth.MAX_SKEW_MS
        assertTrue(SyncAuth.verify(secret, "POST", "/apply", "[8]", edge.toString(), sig("[8]", edge), now))
    }

    @Test fun nonNumericTimestampAndBlankSignatureRejected() {
        assertFalse(SyncAuth.verify(secret, "POST", "/apply", "[9]", "soon", sig("[9]"), now))
        assertFalse(SyncAuth.verify(secret, "POST", "/apply", "[9]", now.toString(), "  ", now))
    }

    @Test fun signatureIsStableAndDependsOnEveryPart() {
        val base = sig("[10]")
        assertEquals(base, sig("[10]"))
        assertNotEquals(base, sig("[11]"))
        assertNotEquals(base, sig("[10]", ts = now + 1))
        assertNotEquals(base, sig("[10]", method = "GET"))
        assertNotEquals(base, sig("[10]", path = "/snapshot"))
        assertEquals(64, base.length)
    }

    @Test fun upperCaseSignatureIsAcceptedOnce() {
        val s = sig("[12]").uppercase()
        assertTrue(SyncAuth.verify(secret, "POST", "/apply", "[12]", now.toString(), s, now))
        assertFalse(SyncAuth.verify(secret, "POST", "/apply", "[12]", now.toString(), s, now))
    }

    @Test fun aFailedAttemptDoesNotUseUpTheRealSignature() {
        val good = sig("[13]")
        assertFalse(SyncAuth.verify(secret, "POST", "/apply", "[13,evil]", now.toString(), good, now))
        assertTrue(SyncAuth.verify(secret, "POST", "/apply", "[13]", now.toString(), good, now))
    }

    @Test fun replayIsStillRejectedAfterARestart() {
        val f = File.createTempFile("sync-replay", ".cache"); f.deleteOnExit()
        val ts = System.currentTimeMillis()
        val s = SyncAuth.sign(secret, "POST", "/apply", "[14]", ts)
        SyncAuth.usePersistentCache(f)
        assertTrue(SyncAuth.verify(secret, "POST", "/apply", "[14]", ts.toString(), s, ts))
        SyncAuth.flushCache()
        SyncAuth.usePersistentCache(f)   // what a restart does
        assertFalse(SyncAuth.verify(secret, "POST", "/apply", "[14]", ts.toString(), s, ts))
    }
}
