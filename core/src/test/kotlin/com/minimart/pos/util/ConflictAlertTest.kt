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

    @Test fun `refused sale names the receipt and reason in both languages`() {
        val en = ConflictAlert.refusedSale("T2-0007", "Not enough stock for Milk")
        assertTrue(en.body.contains("T2-0007") && en.body.contains("Not enough stock for Milk") && en.body.contains("not in your totals"), en.body)
        Lang.arabic = true
        val ar = ConflictAlert.refusedSale("T2-0007", "Not enough stock for Milk")
        assertTrue(ar.title.any { it in '\u0600'..'\u06FF' } && ar.body.contains("T2-0007"), ar.body)
    }

    @Test fun `refused sale text is tidied`() {
        val t = ConflictAlert.refusedSale("", "line one\r\nline two " + "x".repeat(500))
        assertTrue(!t.body.contains('\n') && !t.body.contains('\r') && t.body.length < 400 && t.body.contains("Sale ?"), t.body)
    }
}
