package com.minimart.pos.util

import org.junit.Assert.*
import org.junit.Test

class UnitMathTest {
    @Test fun codesAreTrimmedLowerCasedAndLimited() {
        assertEquals("carton", UnitMath.normalizeCode("  Carton "))
        assertEquals("6-pack", UnitMath.normalizeCode("6-Pack"))
        assertEquals("half  dozen".replace("  ", " "), UnitMath.normalizeCode("Half   Dozen"))
        assertNull(UnitMath.normalizeCode(""))
        assertNull(UnitMath.normalizeCode("   "))
        assertNull(UnitMath.normalizeCode("a".repeat(21)))
        assertNull(UnitMath.normalizeCode("kg;drop"))
    }

    @Test fun aPackHoldsAtLeastTwoUnitsAndASaneMaximum() {
        assertFalse(UnitMath.validFactor(1)); assertFalse(UnitMath.validFactor(0)); assertFalse(UnitMath.validFactor(-6))
        assertTrue(UnitMath.validFactor(2)); assertTrue(UnitMath.validFactor(UnitMath.MAX_FACTOR))
        assertFalse(UnitMath.validFactor(UnitMath.MAX_FACTOR + 1))
        assertEquals(24, UnitMath.parseFactor(" 24 "))
        assertNull(UnitMath.parseFactor("2.5")); assertNull(UnitMath.parseFactor("")); assertNull(UnitMath.parseFactor("1"))
    }

    @Test fun packsConvertToBaseUnitsWithoutOverflow() {
        assertEquals(48, UnitMath.toBase(2, 24))
        assertEquals(0, UnitMath.toBase(0, 24))
        assertNull(UnitMath.toBase(-1, 24))
        assertNull(UnitMath.toBase(Int.MAX_VALUE, 24))
    }

    @Test fun stockSplitsIntoWholePacksAndLooseUnits() {
        assertEquals(3 to 3, UnitMath.split(75, 24))
        assertEquals(0 to 10, UnitMath.split(10, 24))
        assertEquals(0 to 0, UnitMath.split(-5, 24))
    }

    @Test fun describeUsesTheBiggestPackFirst() {
        val packs = listOf(PackSize("dozen", 12), PackSize("carton", 24))
        assertEquals("3 carton + 1 dozen + 3 pcs", UnitMath.describe(87, "pcs", packs))
        assertEquals("1 carton", UnitMath.describe(24, "pcs", packs))
        assertEquals("5 pcs", UnitMath.describe(5, "pcs", packs))
        assertEquals("75 pcs", UnitMath.describe(75, "pcs", emptyList()))
        assertEquals("0 pcs", UnitMath.describe(0, "pcs", packs))
        assertEquals("0 pcs", UnitMath.describe(-3, "pcs", packs))
    }

    @Test fun describeIgnoresBadOrRepeatedFactors() {
        val packs = listOf(PackSize("odd", 1), PackSize("a", 12), PackSize("b", 12))
        assertEquals("2 a + 1 pcs", UnitMath.describe(25, "pcs", packs))
    }

    @Test fun packPriceIsCentExact() {
        assertEquals(1200.0, UnitMath.packPrice(50.0, 24), 0.0)
        assertEquals(3.3, UnitMath.packPrice(1.1, 3), 0.0)
        assertEquals(0.3, UnitMath.packPrice(0.1, 3), 0.0)
    }
}
