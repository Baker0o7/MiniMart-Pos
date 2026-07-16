package com.minimart.pos.di

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import com.minimart.pos.data.dao.*
import com.minimart.pos.data.db.AppDatabase
import com.minimart.pos.data.db.DatabaseCallback
import com.minimart.pos.util.DatabaseKeyManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        callback: DatabaseCallback,
        keyManager: DatabaseKeyManager,
        migrator: com.minimart.pos.util.DbEncryptionMigrator
    ): AppDatabase {
        val passphrase = keyManager.getOrCreateDbKey()

        // Migrate existing plaintext DB to encrypted before Room opens it.
        // On fresh installs or already-encrypted DBs this is a fast no-op.
        migrator.migrateIfNeeded(AppDatabase.DATABASE_NAME, passphrase)

        val factory = net.sqlcipher.database.SupportFactory(
            passphrase.toByteArray(Charsets.UTF_8)
        )

        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        )
            .openHelperFactory(factory)
            .addMigrations(*com.minimart.pos.data.db.AppMigrations.ALL)
            .fallbackToDestructiveMigration()
            .addCallback(callback)
            .build()
    }

    @Provides fun provideProductDao(db: AppDatabase): ProductDao = db.productDao()
    @Provides fun provideSaleDao(db: AppDatabase): SaleDao = db.saleDao()
    @Provides fun provideUserDao(db: AppDatabase): UserDao = db.userDao()
    @Provides fun provideExpenseDao(db: AppDatabase): ExpenseDao = db.expenseDao()
    @Provides @Singleton fun provideShiftDao(db: AppDatabase): ShiftDao = db.shiftDao()
    @Provides @Singleton fun provideCustomerDao(db: AppDatabase) = db.customerDao()
    @Provides @Singleton fun provideSyncDao(db: AppDatabase) = db.syncDao()

    @Provides @Singleton
    fun provideSharedPreferences(@ApplicationContext context: Context): SharedPreferences =
        context.getSharedPreferences("minimart_sync_prefs", Context.MODE_PRIVATE)
}
