package com.minimart.pos.util

import org.junit.Assert.*
import org.junit.Test

class PluDecoderTest {

    @Test
    fun `decode parses a valid PLU barcode correctly`() {
        // Prefix 2, PLU 00001, weight 00050g (0.05kg) — verified by tracing the exact
        // substring bounds in decode(): substring(1,6)="00001", substring(6,11)="00050".
        // (This barcode's real decoded weight is 50g, not 500g as an earlier version of
        // the source docstring incorrectly claimed — corrected there too.)
        val result = PluDecoder.decode("2000010005001")
        assertNotNull(result)
        assertEquals("00001", result!!.pluCode)
        assertEquals(50.0, result.weightGrams, 0.0)
        assertEquals(0.05, result.weightKg, 0.0)
    }

    @Test
    fun `decode handles a different PLU and weight`() {
        // Constructed and verified independently: "2" + PLU"12345" + weight"02350" +
        // 2-digit check region "01" = 13 chars. substring(6,11) = "02350" = 2350g = 2.35kg.
        val result = PluDecoder.decode("2123450235001")
        assertNotNull(result)
        assertEquals("12345", result!!.pluCode)
        assertEquals(2350.0, result.weightGrams, 0.0)
        assertEquals(2.35, result.weightKg, 0.0001)
    }

    @Test
    fun `decode trims whitespace before parsing`() {
        val result = PluDecoder.decode("  2000010005001  ")
        assertNotNull(result)
        assertEquals("00001", result!!.pluCode)
    }

    @Test
    fun `decode returns null for wrong length`() {
        assertNull(PluDecoder.decode("200001000500"))   // 12 digits, too short
        assertNull(PluDecoder.decode("20000100050011")) // 14 digits, too long
        assertNull(PluDecoder.decode(""))
    }

    @Test
    fun `decode returns null for a regular fixed-price barcode`() {
        // A normal EAN-13 not starting with prefix 2 must never be misread as a PLU
        assertNull(PluDecoder.decode("6009000000000"))
        assertNull(PluDecoder.decode("5901234123457"))
    }

    @Test
    fun `decode returns null for non-digit characters`() {
        assertNull(PluDecoder.decode("2ABC010005001"))
        assertNull(PluDecoder.decode("200001000500X"))
    }

    @Test
    fun `isPluBarcode matches decode's own acceptance criteria`() {
        assertTrue(PluDecoder.isPluBarcode("2000010005001"))
        assertFalse(PluDecoder.isPluBarcode("6009000000000")) // wrong prefix
        assertFalse(PluDecoder.isPluBarcode("200001000500"))  // wrong length
        assertFalse(PluDecoder.isPluBarcode("2ABC010005001")) // non-digit
    }

    @Test
    fun `calculatePrice multiplies weight by price per kg`() {
        // 0.5kg at KES 300/kg = KES 150.00
        assertEquals(150.0, PluDecoder.calculatePrice(pricePerKg = 300.0, weightKg = 0.5), 0.0001)
    }

    @Test
    fun `calculatePrice handles a realistic fractional weight`() {
        // 0.537kg at KES 300.33/kg
        // Documents the function's actual behavior: it truncates to 2dp rather than
        // rounding, via (x * 100).toLong() / 100.0 — this test locks that behavior in
        // so a future change to rounding-vs-truncation is caught rather than silent.
        val price = PluDecoder.calculatePrice(pricePerKg = 300.33, weightKg = 0.537)
        assertEquals(161.27, price, 0.001)
    }

    @Test
    fun `calculatePrice with zero weight is zero`() {
        assertEquals(0.0, PluDecoder.calculatePrice(pricePerKg = 300.0, weightKg = 0.0), 0.0)
    }
}
