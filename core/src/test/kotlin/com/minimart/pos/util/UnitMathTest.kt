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

class UnitMathPackPricingTest {
    @Test fun ownPackPriceBeatsTheAutomaticOne() {
        assertEquals(1200.0, UnitMath.packPrice(50.0, 24), 0.0)
        assertEquals(1100.0, UnitMath.packPrice(50.0, 24, 1100.0), 0.0)
        assertEquals(1200.0, UnitMath.packPrice(50.0, 24, 0.0), 0.0)
    }

    @Test fun ownPackCostBeatsTheAutomaticOne() {
        assertEquals(960.0, UnitMath.packCost(40.0, 24, 0.0), 0.0)
        assertEquals(900.0, UnitMath.packCost(40.0, 24, 900.0), 0.0)
    }

    @Test fun packCostGivesTheCostOfOneUnit() {
        assertEquals(37.5, UnitMath.unitCostFromPack(900.0, 24), 1e-9)
        assertEquals(33.3333, UnitMath.unitCostFromPack(100.0, 3), 1e-9)
        assertEquals(0.0, UnitMath.unitCostFromPack(0.0, 24), 0.0)
        assertEquals(0.0, UnitMath.unitCostFromPack(900.0, 0), 0.0)
    }

    @Test fun discountOnlyWhenThePackIsCheaper() {
        assertEquals(8.333, UnitMath.packDiscountPercent(50.0, 24, 1100.0)!!, 0.001)
        assertNull(UnitMath.packDiscountPercent(50.0, 24, 1200.0))
        assertNull(UnitMath.packDiscountPercent(50.0, 24, 1300.0))
        assertNull(UnitMath.packDiscountPercent(0.0, 24, 10.0))
    }

    @Test fun packBarcodesAreCheckedForShapeNotMeaning() {
        assertEquals("", UnitMath.normalizeBarcode("  "))
        assertEquals("6161100123456", UnitMath.normalizeBarcode(" 6161100123456 "))
        assertEquals("CTN-001_a.b", UnitMath.normalizeBarcode("CTN-001_a.b"))
        assertNull(UnitMath.normalizeBarcode("123"))
        assertNull(UnitMath.normalizeBarcode("12 34 56"))
        assertNull(UnitMath.normalizeBarcode("a".repeat(41)))
        assertNull(UnitMath.normalizeBarcode("12345;DROP"))
        assertNull(UnitMath.normalizeBarcode("١٢٣٤٥٦"))
    }
}

class PackDealTest {
    @Test fun aDealIsTheSavingAgainstBuyingSingly() {
        assertEquals(PackDeal(24, 100.0), UnitMath.deal(50.0, 24, 1100.0))
        assertNull(UnitMath.deal(50.0, 24, 1200.0))   // same as singles: no deal
        assertNull(UnitMath.deal(50.0, 24, 1300.0))   // dearer: no deal
        assertNull(UnitMath.deal(50.0, 24, 0.0))      // no own price
        assertNull(UnitMath.deal(50.0, 1, 10.0))      // not a valid pack
    }

    @Test fun wholePacksSaveAndTheRestPaysFullPrice() {
        val carton = PackDeal(24, 100.0)
        assertEquals(0.0, UnitMath.dealDiscount(23, listOf(carton)), 0.0)
        assertEquals(100.0, UnitMath.dealDiscount(24, listOf(carton)), 0.0)
        assertEquals(100.0, UnitMath.dealDiscount(47, listOf(carton)), 0.0)
        assertEquals(200.0, UnitMath.dealDiscount(48, listOf(carton)), 0.0)
    }

    @Test fun biggestPackIsCountedFirst() {
        val deals = listOf(PackDeal(12, 40.0), PackDeal(24, 100.0))
        assertEquals(100.0, UnitMath.dealDiscount(30, deals), 0.0)          // carton, then 6 left: no dozen
        assertEquals(140.0, UnitMath.dealDiscount(36, deals), 0.0)          // carton + dozen
    }

    @Test fun nothingToSaveForEmptyOrNonsenseInput() {
        assertEquals(0.0, UnitMath.dealDiscount(0, listOf(PackDeal(24, 100.0))), 0.0)
        assertEquals(0.0, UnitMath.dealDiscount(100, emptyList()), 0.0)
        assertEquals(0.0, UnitMath.dealDiscount(100, listOf(PackDeal(0, 5.0))), 0.0)
    }

    @Test fun centsStayExact() {
        assertEquals(0.3, UnitMath.dealDiscount(30, listOf(PackDeal(10, 0.1))), 0.0)
    }
}
