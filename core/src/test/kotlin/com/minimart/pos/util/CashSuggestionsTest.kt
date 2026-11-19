package com.minimart.pos.util

import org.junit.Assert.*
import org.junit.Test

class CashSuggestionsTest {
    @Test fun exactTextIsWholeShillingsOrTwoDecimals() {
        assertEquals("250", CashSuggestions.exactText(250.0))
        assertEquals("250.50", CashSuggestions.exactText(250.5))
        assertEquals("0", CashSuggestions.exactText(0.0))
    }

    @Test fun everyRoundUpCoversTheBill() {
        for (total in listOf(1.0, 37.5, 49.99, 50.0, 120.0, 499.0, 999.5, 1000.0, 1234.56, 9999.0, 250_000.0)) {
            val ups = CashSuggestions.roundUps(total)
            assertEquals("three buttons for $total", 3, ups.size)
            assertTrue("all cover $total: $ups", ups.all { it > total })
            assertEquals("ascending and distinct for $total: $ups", ups.sorted().distinct(), ups)
        }
    }

    @Test fun typicalBillGivesNextFiftyHundredFiveHundred() {
        assertEquals(listOf(50L, 100L, 500L), CashSuggestions.roundUps(40.0))
        assertEquals(listOf(300L, 500L, 1000L), CashSuggestions.roundUps(250.0))
    }

    @Test fun billJustUnderABigNoteContinuesUpwards() {
        assertEquals(listOf(1000L, 1500L, 2000L), CashSuggestions.roundUps(999.5))
    }

    @Test fun noBillYetOffersCommonNotes() {
        assertEquals(listOf(100L, 200L, 500L), CashSuggestions.roundUps(0.0))
        assertEquals(listOf(100L, 200L, 500L), CashSuggestions.roundUps(Double.NaN))
    }

    @Test fun labelsHaveThousandsSeparators() {
        assertEquals("1,500", CashSuggestions.label(1500))
        assertEquals("50", CashSuggestions.label(50))
    }

    @Test fun billThatIsAlreadyARoundNoteStillGetsButtonsAboveIt() {
        assertEquals(listOf(1500L, 2000L, 2500L), CashSuggestions.roundUps(1000.0))
    }
}
