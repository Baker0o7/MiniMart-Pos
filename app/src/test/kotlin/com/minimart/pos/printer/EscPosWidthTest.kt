package com.minimart.pos.printer

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class EscPosWidthTest {
    @After fun reset() { EscPos.lineChars = 32 }

    @Test fun smallPaperGetsThirtyTwoColumnsAndWidePaperFortyEight() {
        assertEquals(32, EscPos.charsForPaper(58))
        assertEquals(48, EscPos.charsForPaper(80))
        assertEquals(32, EscPos.charsForPaper(0))
    }

    @Test fun dividerFollowsThePaperWidth() {
        EscPos.lineChars = EscPos.charsForPaper(80)
        // the divider is the line plus a newline
        assertEquals(48 + EscPos.NEWLINE.size, EscPos.divider().size)
        EscPos.lineChars = EscPos.charsForPaper(58)
        assertEquals(32 + EscPos.NEWLINE.size, EscPos.divider().size)
    }
}
