package com.minimart.pos.util

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * The language the owner picked for this app, kept apart from the phone's language so a shop can run the till in
 * Arabic on an English phone. Stored in a tiny plain preference (it is not a secret) and applied in
 * `MainActivity.attachBaseContext`, which is also what flips the layout to right-to-left for Arabic.
 */
object AppLanguage {
    const val SYSTEM = ""
    /** Languages with translated text. Names are written in their own language so anyone can find theirs. */
    val options = listOf(SYSTEM to "", "en" to "English", "ar" to "العربية")

    private const val PREFS = "language"
    private const val KEY = "code"

    fun saved(context: Context): String =
        runCatching { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, SYSTEM).orEmpty() }.getOrDefault(SYSTEM)
            .takeIf { code -> options.any { it.first == code } } ?: SYSTEM

    fun save(context: Context, code: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, code).apply()
    }

    /** A context whose resources use the saved language; the original when following the phone. */
    fun wrap(base: Context): Context {
        val code = saved(base)
        if (code == SYSTEM) return base
        val locale = Locale.forLanguageTag(code)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }

    fun isRtl(code: String): Boolean = code.isNotEmpty() && android.text.TextUtils.getLayoutDirectionFromLocale(Locale.forLanguageTag(code)) == android.view.View.LAYOUT_DIRECTION_RTL
}
