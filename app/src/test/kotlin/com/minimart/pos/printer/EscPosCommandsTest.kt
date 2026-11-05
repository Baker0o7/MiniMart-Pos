package com.minimart.pos.printer

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The bytes that go to the printer and the cash drawer: wrong ones fire the wrong pin or print garbage. */
class EscPosCommandsTest {
    private fun b(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    @Test fun theDrawerKickUsesTheStandardCommandForEitherPin() {
        assertArrayEquals(b(0x1B, 0x70, 0x00, 0x32, 0xFA), CashDrawerManager.kickCommand(0))
        assertArrayEquals(b(0x1B, 0x70, 0x01, 0x32, 0xFA), CashDrawerManager.kickCommand(1))
    }

    @Test fun theDefaultKickIsPinTwo() {
        assertArrayEquals(CashDrawerManager.kickCommand(0), CashDrawerManager.kickCommand())
    }

    @Test fun theStandardControlCommandsAreExactlyRight() {
        assertArrayEquals(b(0x1B, 0x40), EscPos.INIT)
        assertArrayEquals(b(0x1D, 0x56, 0x41, 0x05), EscPos.CUT)
        assertArrayEquals(b(0x1B, 0x61, 0x00), EscPos.ALIGN_LEFT)
        assertArrayEquals(b(0x1B, 0x61, 0x01), EscPos.ALIGN_CENTER)
        assertArrayEquals(b(0x1B, 0x61, 0x02), EscPos.ALIGN_RIGHT)
        assertArrayEquals(b(0x1B, 0x45, 0x01), EscPos.BOLD_ON)
        assertArrayEquals(b(0x1B, 0x45, 0x00), EscPos.BOLD_OFF)
    }

    @Test fun textFromACustomerOrProductNameCanNeverBecomeAPrinterCommand() {
        val hostile = "Soda\u001B@\u001DVA\u0000 \u001Bp\u0000 Fanta\u0007\r"
        val out = EscPos.text(hostile)
        assertTrue("only the newline may be a control byte", out.all { it.toInt() == 0x0A || it in 0x20..0x7E })
        assertTrue(String(out, Charsets.US_ASCII).contains("Soda") && String(out, Charsets.US_ASCII).contains("Fanta"))
    }

    @Test fun accentedAndSwahiliTextPrintsAsPlainLetters() {
        assertEquals("Maji ya Nyumba - 5 x 2", String(EscPos.text("Maji ya Nyumba – 5 × 2"), Charsets.US_ASCII))
        assertEquals("Cafe Creme", String(EscPos.text("Café Crème"), Charsets.US_ASCII))
    }

    @Test fun aReceiptLineIsNeverLongerThanThePaper() {
        EscPos.lineChars = 32
        try { assertEquals(32 + 1, EscPos.divider('=').size) } finally { EscPos.lineChars = 32 }
    }
}
