package com.minimart.pos.di

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import com.minimart.pos.data.dao.*
import com.minimart.pos.data.db.AppDatabase
import com.minimart.pos.data.db.DatabaseCallback
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

/** Distinguishes the encrypted-at-rest SharedPreferences store (for security-sensitive
 * values like the sync pairing secret) from the regular one Hilt would otherwise
 * ambiguously match against, since both are plain SharedPreferences-typed bindings. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SecurePrefs

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        callback: DatabaseCallback
    ): AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        AppDatabase.DATABASE_NAME
    )
        .addMigrations(*com.minimart.pos.data.db.AppMigrations.ALL)
        .fallbackToDestructiveMigration()
        .addCallback(callback)
        .build()

    @Provides fun provideProductDao(db: AppDatabase): ProductDao = db.productDao()
    @Provides fun provideSaleDao(db: AppDatabase): SaleDao = db.saleDao()
    @Provides fun provideUserDao(db: AppDatabase): UserDao = db.userDao()
    @Provides fun provideExpenseDao(db: AppDatabase): ExpenseDao = db.expenseDao()
    @Provides @Singleton fun provideShiftDao(db: AppDatabase): ShiftDao = db.shiftDao()
    @Provides @Singleton fun provideCustomerDao(db: AppDatabase) = db.customerDao()
    @Provides @Singleton fun provideSyncDao(db: AppDatabase) = db.syncDao()

    /** Creates an EncryptedSharedPreferences-backed store for the given file name, with
     * a defensive fallback to plain SharedPreferences if Keystore/security-crypto setup
     * fails for any reason on a given device. This project has twice suffered real
     * crashes from crypto-related native-library issues (SQLCipher, attempted and
     * reverted twice) — the app must never fail to launch over an encryption feature,
     * so a failure here degrades to "this one file isn't encrypted" rather than
     * crashing startup. androidx.security:security-crypto is architecturally different
     * from SQLCipher though: it's a thin wrapper around the system Android Keystore
     * service (already present on every device), not a bundled third-party native
     * library — so this fallback is expected to be needed rarely, if ever. */
    private fun buildEncryptedPrefs(context: Context, fileName: String): SharedPreferences =
        try {
            val masterKey = androidx.security.crypto.MasterKey.Builder(context)
                .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
                .build()
            androidx.security.crypto.EncryptedSharedPreferences.create(
                context,
                fileName,
                masterKey,
                androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            context.getSharedPreferences(fileName, Context.MODE_PRIVATE)
        }

    // Bug fix: the remembered peer_sync_key (another device's pairing code, entered
    // when this device acts as a sync CLIENT) was stored in a plain, unencrypted
    // SharedPreferences file — a plaintext copy of a security credential, despite the
    // server side of that same credential already being hardened with rate limiting
    // and constant-time comparison. EncryptedSharedPreferences.create() returns a
    // standard SharedPreferences-compatible object, so this was a drop-in swap — zero
    // changes needed to SyncViewModel's actual read/write code.
    @Provides @Singleton
    fun provideSharedPreferences(@ApplicationContext context: Context): SharedPreferences =
        buildEncryptedPrefs(context, "minimart_sync_prefs")

    // Bug fix: this device's OWN sync pairing secret (shown when acting as a sync
    // server) was stored in plaintext Preferences DataStore. Preferences DataStore has
    // no first-party encrypted variant, so a dedicated encrypted SharedPreferences file
    // is used for this one sensitive value instead (see SettingsRepository's
    // getOrCreateSyncSecret(), which also migrates any already-generated plaintext
    // secret from the old DataStore location on first read, so already-paired devices
    // don't suddenly get a mismatched secret and silently break sync).
    @Provides @Singleton @SecurePrefs
    fun provideSecurePreferences(@ApplicationContext context: Context): SharedPreferences =
        buildEncryptedPrefs(context, "minimart_secure_prefs")
}
