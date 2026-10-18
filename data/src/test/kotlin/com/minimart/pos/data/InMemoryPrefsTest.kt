package com.minimart.pos.data

import com.minimart.pos.data.security.InMemoryPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InMemoryPrefsTest {
    @Test fun storesAndReadsEveryType() {
        val p = InMemoryPrefs()
        p.edit().putString("s", "x").putInt("i", 3).putLong("l", 4L).putFloat("f", 1.5f).putBoolean("b", true).commit()
        assertEquals("x", p.getString("s", null)); assertEquals(3, p.getInt("i", 0)); assertEquals(4L, p.getLong("l", 0))
        assertEquals(1.5f, p.getFloat("f", 0f), 0f); assertTrue(p.getBoolean("b", false))
    }

    @Test fun defaultsAreReturnedForMissingKeys() {
        val p = InMemoryPrefs()
        assertEquals("d", p.getString("nope", "d")); assertFalse(p.contains("nope")); assertEquals(7, p.getInt("nope", 7))
    }

    @Test fun removeAndClearWork() {
        val p = InMemoryPrefs()
        p.edit().putString("a", "1").putString("b", "2").commit()
        p.edit().remove("a").commit()
        assertFalse(p.contains("a")); assertTrue(p.contains("b"))
        p.edit().clear().commit()
        assertFalse(p.contains("b"))
    }

    @Test fun anInMemoryStoreNeverSharesStateWithAnotherInstance() {
        val a = InMemoryPrefs(); val b = InMemoryPrefs()
        a.edit().putString("secret", "x").commit()
        assertEquals(null, b.getString("secret", null))
    }
}
