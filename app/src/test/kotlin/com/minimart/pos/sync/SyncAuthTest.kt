package com.minimart.pos.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncAuthTest {
    private val secret = "123456"
    private val now = 1_800_000_000_000L

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
        assertFalse(SyncAuth.verify("000000", "POST", "/apply", "[4]", now.toString(), sig("[4]"), now))
        assertFalse(SyncAuth.verify(secret, "GET", "/snapshot", "[5]", now.toString(), sig("[5]"), now))
    }

    @Test fun staleTimestampRejected() {
        val old = now - SyncAuth.MAX_SKEW_MS - 1
        assertFalse(SyncAuth.verify(secret, "POST", "/apply", "[6]", old.toString(), sig("[6]", old), now))
    }

    @Test fun missingHeadersRejected() {
        assertFalse(SyncAuth.verify(secret, "POST", "/apply", "", null, null, now))
    }
}
