package com.minimart.pos.di

import javax.inject.Qualifier

/** Distinguishes the encrypted-at-rest SharedPreferences store (for security-sensitive
 * values like the sync pairing secret) from the regular one Hilt would otherwise
 * ambiguously match against, since both are plain SharedPreferences-typed bindings. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SecurePrefs
