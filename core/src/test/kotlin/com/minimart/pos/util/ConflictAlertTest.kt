package com.minimart.pos.util

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConflictAlertTest {
    @AfterEach fun reset() { Lang.arabic = false }

    @Test fun `english names the sale and what was kept`() {
        val t = ConflictAlert.reversal("R-0042", "REFUNDED", "VOIDED")
        assertTrue(t.body.contains("R-0042") && t.body.contains("already refunded") && t.body.contains("voided"), t.body)
    }

    @Test fun `arabic version is arabic and still names the sale`() {
        Lang.arabic = true
        val t = ConflictAlert.reversal("R-0042", "REFUNDED", "VOIDED")
        assertTrue(t.title.any { it in '\u0600'..'\u06FF' } && t.body.contains("R-0042") && t.body.contains("مستردة") && t.body.contains("إلغائها"), t.body)
    }
}
