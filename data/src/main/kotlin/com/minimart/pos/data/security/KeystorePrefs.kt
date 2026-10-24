package com.minimart.pos.data.security

import android.content.SharedPreferences
import com.minimart.pos.util.AesGcmBox
import org.json.JSONArray
import java.util.concurrent.CopyOnWriteArraySet

/**
 * SharedPreferences whose values are AES-256-GCM encrypted with a Keystore key (see [AesGcmBox]).
 * Replaces the alpha androidx.security EncryptedSharedPreferences: same interface for callers, but a
 * small format we can read and test ourselves. Key names are stored as they are (they are not secret);
 * each value is bound to its file and key name, so values cannot be swapped around in the file.
 * A value that cannot be opened (restored onto another phone, or altered) reads as missing.
 */
class KeystorePrefs(
    private val store: SharedPreferences,
    private val box: AesGcmBox,
    private val name: String
) : SharedPreferences {
    private val listeners = CopyOnWriteArraySet<SharedPreferences.OnSharedPreferenceChangeListener>()

    private fun aad(key: String) = "$name|$key"

    private fun read(key: String): String? {
        val sealed = store.getString(key, null) ?: return null
        return box.open(aad(key), sealed)?.toString(Charsets.UTF_8)
    }

    private fun decode(raw: String): Any? {
        if (raw.length < 2 || raw[1] != ':') return null
        val v = raw.substring(2)
        return try {
            when (raw[0]) {
                's' -> v
                'i' -> v.toInt()
                'l' -> v.toLong()
                'f' -> v.toFloat()
                'b' -> v.toBooleanStrict()
                'S' -> JSONArray(v).let { a -> (0 until a.length()).mapTo(LinkedHashSet()) { a.getString(it) } }
                else -> null
            }
        } catch (_: Exception) { null }
    }

    private inline fun <reified T> get(key: String, def: T): T = read(key)?.let { decode(it) } as? T ?: def

    override fun getAll(): MutableMap<String, *> {
        val out = HashMap<String, Any?>()
        store.all.keys.forEach { k -> read(k)?.let { decode(it) }?.let { out[k] = it } }
        return out
    }
    override fun getString(key: String, defValue: String?): String? = get<String?>(key, defValue)
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
        (read(key)?.let { decode(it) } as? Set<String>)?.toMutableSet() ?: defValues
    override fun getInt(key: String, defValue: Int): Int = get(key, defValue)
    override fun getLong(key: String, defValue: Long): Long = get(key, defValue)
    override fun getFloat(key: String, defValue: Float): Float = get(key, defValue)
    override fun getBoolean(key: String, defValue: Boolean): Boolean = get(key, defValue)
    override fun contains(key: String): Boolean = read(key)?.let { decode(it) } != null
    override fun edit(): SharedPreferences.Editor = Ed()
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) { listeners.add(l) }
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) { listeners.remove(l) }

    private inner class Ed : SharedPreferences.Editor {
        private val pending = LinkedHashMap<String, Any?>()
        private var clear = false
        private fun put(k: String, v: Any?) = apply { pending[k] = v }
        override fun putString(key: String, value: String?) = put(key, value?.let { "s:$it" })
        override fun putStringSet(key: String, values: MutableSet<String>?) = put(key, values?.let { "S:" + JSONArray(it.toList()) })
        override fun putInt(key: String, value: Int) = put(key, "i:$value")
        override fun putLong(key: String, value: Long) = put(key, "l:$value")
        override fun putFloat(key: String, value: Float) = put(key, "f:$value")
        override fun putBoolean(key: String, value: Boolean) = put(key, "b:$value")
        override fun remove(key: String) = put(key, null)
        override fun clear() = apply { clear = true }

        private fun write(sync: Boolean): Boolean {
            val e = store.edit()
            try {
                if (clear) e.clear()
                pending.forEach { (k, v) ->
                    if (v == null) e.remove(k) else e.putString(k, box.seal(aad(k), (v as String).toByteArray(Charsets.UTF_8)))
                }
            } catch (_: Exception) { return false }   // Keystore refused: nothing is written
            val ok = if (sync) e.commit() else { e.apply(); true }
            if (ok) listeners.forEach { l -> pending.keys.forEach { k -> l.onSharedPreferenceChanged(this@KeystorePrefs, k) } }
            return ok
        }
        override fun commit(): Boolean = write(true)
        override fun apply() { write(false) }
    }
}
