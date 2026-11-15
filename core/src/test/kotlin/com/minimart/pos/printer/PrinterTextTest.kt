package com.minimart.pos.printer

import org.junit.Assert.assertEquals
import org.junit.Test

class PrinterTextTest {
    @Test fun plainAsciiIsUnchanged() = assertEquals("Maziwa 500ml  KES 65.00", PrinterText.ascii("Maziwa 500ml  KES 65.00"))
    @Test fun accentsAreStripped() = assertEquals("Jose Munoz", PrinterText.ascii("José Muñoz"))
    @Test fun symbolsAreReplaced() = assertEquals("2 x 50.00 - paid", PrinterText.ascii("2 × 50.00 • paid"))
    @Test fun unknownCharactersBecomeQuestionMarks() = assertEquals("??", PrinterText.ascii("日本"))
    @Test fun controlBytesAreDroppedButNewlinesStay() = assertEquals("A@B\nC", PrinterText.ascii("A\u001B@B\n\u001DC"))
}
