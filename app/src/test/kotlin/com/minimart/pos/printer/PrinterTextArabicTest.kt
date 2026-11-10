package com.minimart.pos.printer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PrinterTextArabicTest {
    @Test fun arabicNamesPrintInLatinLettersNotQuestionMarks() {
        val out = PrinterText.ascii("حليب")
        assertEquals("hlyb", out)
        assertFalse(out.contains('?'))
    }

    @Test fun arabicIndicDigitsBecomeNormalDigits() = assertEquals("KSh 1250", PrinterText.ascii("KSh ١٢٥٠"))

    @Test fun diacriticsAreDropped() = assertEquals("mhmd", PrinterText.ascii("مُحَمَّد"))

    @Test fun englishIsUnchanged() = assertEquals("Milk 500ml", PrinterText.ascii("Milk 500ml"))
}
