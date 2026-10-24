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
        // Only the pre-release schemas (v1–v7) have no migration path. A missing migration for any
        // later version must fail loudly, not silently wipe the shop's sales and stock.
        .fallbackToDestructiveMigrationFrom(1, 2, 3, 4, 5, 6, 7)
        .addCallback(callback)
        .build()

    @Provides fun provideProductDao(db: AppDatabase): ProductDao = db.productDao()
    @Provides fun provideSaleDao(db: AppDatabase): SaleDao = db.saleDao()
    @Provides fun provideUserDao(db: AppDatabase): UserDao = db.userDao()
    @Provides fun provideExpenseDao(db: AppDatabase): ExpenseDao = db.expenseDao()
    @Provides @Singleton fun provideShiftDao(db: AppDatabase): ShiftDao = db.shiftDao()
    @Provides @Singleton fun provideCustomerDao(db: AppDatabase) = db.customerDao()
    @Provides @Singleton fun provideSyncDao(db: AppDatabase) = db.syncDao()
    @Provides @Singleton fun provideMpesaPaymentDao(db: AppDatabase) = db.mpesaPaymentDao()
    @Provides @Singleton fun providePurchasingDao(db: AppDatabase) = db.purchasingDao()
    @Provides @Singleton fun provideBatchDao(db: AppDatabase) = db.batchDao()

    /** Creates the encrypted store for [fileName]: AES-256-GCM with a key held in the Android Keystore
     * ([KeystorePrefs]). If the phone's Keystore cannot be used, the app must still start, but it must never
     * fall back to a plain file that would hold pairing codes and M-Pesa credentials readable. The fallback is
     * therefore an in-memory store: nothing is written to disk, values are lost on restart, and callers that
     * need to persist a secret check for [InMemoryPrefs] and tell the user (see
     * SettingsRepository.secureStorageAvailable). Values from the older androidx.security file
     * [legacyName] are carried over once, then that file is deleted. */
    private fun buildSecurePrefs(context: Context, fileName: String, legacyName: String): SharedPreferences =
        try {
            val box = com.minimart.pos.util.AesGcmBox(com.minimart.pos.data.security.KeystoreKey.getOrCreate("minimart_prefs_key_v2"))
            // Some phones create a Keystore key fine but fail when using it; find out now, not at the first sale.
            check(box.open("selftest", box.seal("selftest", byteArrayOf(1, 2, 3)))?.size == 3) { "Keystore self-test failed" }
            val prefs = com.minimart.pos.data.security.KeystorePrefs(
                context.getSharedPreferences(fileName, Context.MODE_PRIVATE), box, fileName)
            LegacySecurePrefsMigrator.migrate(context, legacyName, prefs)
            prefs
        } catch (e: Exception) {
            android.util.Log.e("SecureStorage", "Keystore unavailable for $fileName; using memory only", e)
            com.minimart.pos.data.security.InMemoryPrefs()
        }

    // Bug fix: the remembered peer_sync_key (another device's pairing code, entered
    // when this device acts as a sync CLIENT) was stored in a plain, unencrypted
    // SharedPreferences file — a plaintext copy of a security credential, despite the
    // server side of that same credential already being hardened with rate limiting
    // and constant-time comparison. The encrypted store is a
    // standard SharedPreferences-compatible object, so this was a drop-in swap — zero
    // changes needed to SyncViewModel's actual read/write code.
    @Provides @Singleton
    fun provideSharedPreferences(@ApplicationContext context: Context): SharedPreferences =
        buildSecurePrefs(context, "minimart_sync_prefs_v2", "minimart_sync_prefs")

    // Bug fix: this device's OWN sync pairing secret (shown when acting as a sync
    // server) was stored in plaintext Preferences DataStore. Preferences DataStore has
    // no first-party encrypted variant, so a dedicated encrypted SharedPreferences file
    // is used for this one sensitive value instead (see SettingsRepository's
    // getOrCreateSyncSecret(), which also migrates any already-generated plaintext
    // secret from the old DataStore location on first read, so already-paired devices
    // don't suddenly get a mismatched secret and silently break sync).
    @Provides @Singleton @SecurePrefs
    fun provideSecurePreferences(@ApplicationContext context: Context): SharedPreferences =
        buildSecurePrefs(context, "minimart_secure_prefs_v2", "minimart_secure_prefs")
}
