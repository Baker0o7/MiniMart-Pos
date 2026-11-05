package com.minimart.pos.util

/**
 * Application-level encryption for single text fields, in plain Kotlin (AES-256-GCM through [AesGcmBox]).
 * Why it exists on top of the encrypted database: a manual backup is a plain copy of the database that
 * owners put on Drive or WhatsApp, and it should not hand over the names and phone numbers of the people
 * who paid by M-Pesa. A sealed field opens only on a phone that holds the Keystore key, and only as the
 * field it was sealed for ([field] is authenticated), so a value pasted into another column is unreadable.
 *
 * Reading never fails: text that is not sealed (rows from before this existed) is returned as it is, and
 * a sealed value that cannot be opened reads as "". Blank text is never sealed, so "no phone" stays visible.
 * Passing a null box (the Keystore could not be used) stores text as it is and leaves the database's own
 * encryption to protect it.
 */
class FieldCipher(private val box: AesGcmBox?) {
    fun protect(field: String, plain: String): String {
        if (box == null || plain.isBlank() || plain.startsWith(PREFIX)) return plain
        return PREFIX + box.seal(aad(field), plain.toByteArray(Charsets.UTF_8))
    }

    fun reveal(field: String, stored: String): String {
        if (!stored.startsWith(PREFIX)) return stored
        val b = box ?: return ""
        return b.open(aad(field), stored.removePrefix(PREFIX))?.toString(Charsets.UTF_8) ?: ""
    }

    fun isSealed(stored: String): Boolean = stored.startsWith(PREFIX)

    /** True when [protect] would change [stored]: it holds text and is not sealed yet. */
    fun needsSealing(stored: String): Boolean = box != null && stored.isNotBlank() && !isSealed(stored)

    private fun aad(field: String) = "field|$field"

    companion object {
        const val PREFIX = "enc1:"
        /** Stores text as it is. */
        val NONE = FieldCipher(null)
    }
}
