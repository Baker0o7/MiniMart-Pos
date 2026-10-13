package com.minimart.pos.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BarcodeNormalizerTest {
    @Test fun trimsWhitespace() = assertEquals("6161100123456", BarcodeNormalizer.normalize("  6161100123456\n"))
    @Test fun rejectsNullAndBlank() {
        assertNull(BarcodeNormalizer.normalize(null))
        assertNull(BarcodeNormalizer.normalize("   "))
    }
    @Test fun rejectsControlCharacters() = assertNull(BarcodeNormalizer.normalize("123\u0000456"))
    @Test fun rejectsOverlong() = assertNull(BarcodeNormalizer.normalize("1".repeat(BarcodeNormalizer.MAX_LENGTH + 1)))
    @Test fun acceptsMaxLength() = assertEquals(BarcodeNormalizer.MAX_LENGTH, BarcodeNormalizer.normalize("A".repeat(BarcodeNormalizer.MAX_LENGTH))?.length)
}
