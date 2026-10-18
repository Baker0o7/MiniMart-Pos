package com.minimart.pos.data.security

import android.content.SharedPreferences
import java.util.concurrent.ConcurrentHashMap

/**
 * Stand-in for the encrypted preferences file when the phone's Keystore cannot be used. Values live only in
 * memory and are never written to disk, so a secret is lost on restart instead of being left readable in a
 * plain file. (The old fallback silently wrote a plain SharedPreferences file.)
 */
class InMemoryPrefs : SharedPreferences {
    private val map = ConcurrentHashMap<String, Any>()
    private val listeners = java.util.Collections.newSetFromMap(ConcurrentHashMap<SharedPreferences.OnSharedPreferenceChangeListener, Boolean>())

    override fun getAll(): MutableMap<String, *> = HashMap(map)
    override fun getString(key: String, defValue: String?): String? = map[key] as? String ?: defValue
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
        (map[key] as? Set<String>)?.toMutableSet() ?: defValues
    override fun getInt(key: String, defValue: Int): Int = map[key] as? Int ?: defValue
    override fun getLong(key: String, defValue: Long): Long = map[key] as? Long ?: defValue
    override fun getFloat(key: String, defValue: Float): Float = map[key] as? Float ?: defValue
    override fun getBoolean(key: String, defValue: Boolean): Boolean = map[key] as? Boolean ?: defValue
    override fun contains(key: String): Boolean = map.containsKey(key)
    override fun edit(): SharedPreferences.Editor = Ed()
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) { listeners.add(l) }
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) { listeners.remove(l) }

    private inner class Ed : SharedPreferences.Editor {
        private val pending = HashMap<String, Any?>()
        private var clear = false
        private fun put(k: String, v: Any?) = apply { pending[k] = v }
        override fun putString(key: String, value: String?) = put(key, value)
        override fun putStringSet(key: String, values: MutableSet<String>?) = put(key, values?.toSet())
        override fun putInt(key: String, value: Int) = put(key, value)
        override fun putLong(key: String, value: Long) = put(key, value)
        override fun putFloat(key: String, value: Float) = put(key, value)
        override fun putBoolean(key: String, value: Boolean) = put(key, value)
        override fun remove(key: String) = put(key, null)
        override fun clear() = apply { clear = true }
        override fun commit(): Boolean {
            if (clear) map.clear()
            pending.forEach { (k, v) -> if (v == null) map.remove(k) else map[k] = v }
            listeners.forEach { l -> pending.keys.forEach { k -> l.onSharedPreferenceChanged(this@InMemoryPrefs, k) } }
            return true
        }
        override fun apply() { commit() }
    }
}
