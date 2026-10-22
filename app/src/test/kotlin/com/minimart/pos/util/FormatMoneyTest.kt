package com.minimart.pos.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatMoneyTest {
    @Test fun smallAmountsKeepTwoDecimals() {
        assertEquals("KES 0.00", 0.0.formatMoney())
        assertEquals("KES 50.00", 50.0.formatMoney())
        assertEquals("KES 999.99", 999.99.formatMoney())
    }

    @Test fun thousandsAreGroupedSoBigAmountsAreReadable() {
        assertEquals("KES 1,250.00", 1250.0.formatMoney())
        assertEquals("KES 1,234,567.50", 1_234_567.5.formatMoney())
    }

    @Test fun negativeAmountsKeepTheirSign() {
        assertEquals("KES -1,250.00", (-1250.0).formatMoney())
    }

    @Test fun currencyIsWhateverTheShopChose() {
        assertEquals("USD 12.50", 12.5.formatMoney("USD"))
    }

    @Test fun compactFormUsesKAndM() {
        assertEquals("KES 1.5K", 1500.0.formatMoneyCompact())
        assertEquals("KES 2.3M", 2_300_000.0.formatMoneyCompact())
        assertEquals("KES 500.00", 500.0.formatMoneyCompact())
    }
}
