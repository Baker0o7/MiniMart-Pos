package com.minimart.pos.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.minimart.pos.data.security.KeystorePrefs
import com.minimart.pos.util.AesGcmBox
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.crypto.KeyGenerator

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KeystorePrefsTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private fun file(n: String) = ctx.getSharedPreferences(n, Context.MODE_PRIVATE)
    private fun prefs(n: String = "t1", k: javax.crypto.SecretKey = key) = KeystorePrefs(file(n), AesGcmBox(k), n)

    @Test fun storesAndReadsEveryType() {
        val p = prefs()
        p.edit().putString("s", "x").putInt("i", 3).putLong("l", 4L).putFloat("f", 1.5f).putBoolean("b", true)
            .putStringSet("set", mutableSetOf("a", "b")).commit()
        assertEquals("x", p.getString("s", null)); assertEquals(3, p.getInt("i", 0)); assertEquals(4L, p.getLong("l", 0))
        assertEquals(1.5f, p.getFloat("f", 0f), 0f); assertTrue(p.getBoolean("b", false))
        assertEquals(setOf("a", "b"), p.getStringSet("set", null))
        assertEquals(6, p.all.size)
    }

    @Test fun valuesOnDiskAreNotReadable() {
        prefs("t2").edit().putString("pw", "hunter2-hunter2").putLong("n", 123456789L).commit()
        val raw = file("t2").all.values.joinToString()
        assertFalse(raw.contains("hunter2")); assertFalse(raw.contains("123456789"))
    }

    @Test fun survivesReopeningWithTheSameKey() {
        prefs("t3").edit().putString("k", "v").commit()
        assertEquals("v", prefs("t3").getString("k", null))
    }

    @Test fun anotherKeyReadsNothing() {
        prefs("t4").edit().putString("k", "v").commit()
        val other = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val p = prefs("t4", other)
        assertNull(p.getString("k", null)); assertFalse(p.contains("k")); assertTrue(p.all.isEmpty())
    }

    @Test fun aValueMovedToAnotherNameDoesNotOpen() {
        val p = prefs("t5"); p.edit().putString("a", "v").commit()
        val sealed = file("t5").getString("a", null)!!
        file("t5").edit().putString("b", sealed).commit()
        assertNull(p.getString("b", null)); assertEquals("v", p.getString("a", null))
    }

    @Test fun removeClearAndDefaults() {
        val p = prefs("t6")
        p.edit().putString("a", "1").putString("b", "2").commit()
        p.edit().remove("a").commit()
        assertFalse(p.contains("a")); assertEquals("d", p.getString("a", "d"))
        p.edit().putString("b", null).commit(); assertFalse(p.contains("b"))
        p.edit().putInt("n", 1).commit(); p.edit().clear().commit(); assertEquals(9, p.getInt("n", 9))
    }

    @Test fun wrongTypeReadsAsDefault() {
        val p = prefs("t7"); p.edit().putString("s", "abc").commit()
        assertEquals(5, p.getInt("s", 5)); assertTrue(p.getBoolean("s", true))
    }

    @Test fun listenersHearAboutChanges() {
        val p = prefs("t8"); val seen = mutableListOf<String>()
        val l = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, k -> seen += k!! }
        p.registerOnSharedPreferenceChangeListener(l)
        p.edit().putString("x", "1").commit()
        p.unregisterOnSharedPreferenceChangeListener(l)
        p.edit().putString("y", "1").commit()
        assertEquals(listOf("x"), seen)
    }
}
