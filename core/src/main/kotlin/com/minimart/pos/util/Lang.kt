package com.minimart.pos.util

/**
 * Which language the plain-language messages built inside `:core` should use. `:core` has no Android
 * resources, so the app sets [arabic] at start-up from the language the owner picked; English stays the
 * default so every existing test and log line reads the same as before.
 */
object Lang {
    @Volatile var arabic: Boolean = false

    /** The English text, or the Arabic text when the app is running in Arabic. */
    fun t(en: String, ar: String): String = if (arabic) ar else en
}
