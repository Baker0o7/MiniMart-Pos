package com.minimart.pos.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.*
import org.junit.Test

class PaletteTest {
    private fun contrast(a: Color, b: Color): Float {
        val l1 = maxOf(a.luminance(), b.luminance()); val l2 = minOf(a.luminance(), b.luminance())
        return (l1 + 0.05f) / (l2 + 0.05f)
    }

    @Test fun hslPrimaries() {
        assertEquals(Color(1f, 0f, 0f), hslColor(0f, 1f, .5f))
        assertEquals(Color(0f, 1f, 0f), hslColor(120f, 1f, .5f))
        assertEquals(Color(0f, 0f, 1f), hslColor(240f, 1f, .5f))
        assertEquals(Color(0f, 0f, 0f), hslColor(77f, .5f, 0f))
        val gray = hslColor(200f, 0f, .5f)
        assertEquals(gray.red, gray.green, 1e-6f); assertEquals(gray.green, gray.blue, 1e-6f)
    }

    @Test fun hueWrapsAround() {
        val a = hslColor(10f, .6f, .4f); val b = hslColor(370f, .6f, .4f); val c = hslColor(-350f, .6f, .4f)
        assertEquals(a.red, b.red, 1e-5f); assertEquals(a.red, c.red, 1e-5f)
    }

    @Test fun tealIsTheOriginalPalette() {
        val p = AppAccent.TEAL.palette
        assertEquals(Color(0xFF0A1A18), p.bg); assertEquals(Color(0xFF112420), p.surface)
        assertEquals(Color(0xFF00897B), p.accent); assertEquals(Color(0xFFE0F2F1), p.onSurface)
    }

    @Test fun everyAccentIsReadable() {
        AppAccent.entries.forEach { a ->
            val p = a.palette
            assertTrue("${a.name} text on surface", contrast(p.onSurface, p.surface) >= 7f)
            assertTrue("${a.name} secondary text on surface", contrast(p.subText, p.surface) >= 4.5f)
            assertTrue("${a.name} white on accent buttons", contrast(Color.White, p.accent) >= 3f)
            assertTrue("${a.name} accent text on bg", contrast(p.accentLight, p.bg) >= 4.5f)
        }
    }

    @Test fun unknownNameFallsBackToTeal() {
        assertEquals(AppAccent.TEAL, AppAccent.fromName(null))
        assertEquals(AppAccent.TEAL, AppAccent.fromName("PINK"))
        assertEquals(AppAccent.ROSE, AppAccent.fromName("ROSE"))
    }
}
