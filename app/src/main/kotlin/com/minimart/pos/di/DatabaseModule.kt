package com.minimart.pos.di

import android.content.Context
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
import net.sqlcipher.database.SupportFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        callback: DatabaseCallback,
        keyManager: DatabaseKeyManager
    ): AppDatabase {
        // If an unencrypted DB exists from a previous install, delete it first.
        // SQLCipher cannot open a plain SQLite file — it throws before Room
        // can run fallbackToDestructiveMigration().
        val dbFile = context.getDatabasePath(AppDatabase.DATABASE_NAME)
        if (dbFile.exists()) {
            try {
                // Quick check: SQLite plain files start with "SQLite format 3\000"
                val header = ByteArray(16)
                dbFile.inputStream().use { it.read(header) }
                val plainMagic = "SQLite format 3\u0000".toByteArray(Charsets.UTF_8)
                if (header.take(16) == plainMagic.toList()) {
                    android.util.Log.w("DatabaseModule", "Deleting unencrypted DB — migrating to SQLCipher")
                    dbFile.delete()
                    context.getDatabasePath("${AppDatabase.DATABASE_NAME}-shm").delete()
                    context.getDatabasePath("${AppDatabase.DATABASE_NAME}-wal").delete()
                }
            } catch (_: Exception) { /* if check fails, let Room handle it */ }
        }

        val keyBytes = keyManager.getOrCreateKey()
        val factory  = SupportFactory(keyBytes)
        keyBytes.fill(0)

        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        )
            .fallbackToDestructiveMigration()
            .addCallback(callback)
            .openHelperFactory(factory)
            .build()
    }

    @Provides fun provideProductDao(db: AppDatabase): ProductDao = db.productDao()
    @Provides fun provideSaleDao(db: AppDatabase): SaleDao = db.saleDao()
    @Provides fun provideUserDao(db: AppDatabase): UserDao = db.userDao()
    @Provides fun provideExpenseDao(db: AppDatabase): ExpenseDao = db.expenseDao()
    @Provides @Singleton fun provideShiftDao(db: AppDatabase): ShiftDao = db.shiftDao()
    @Provides @Singleton fun provideCustomerDao(db: AppDatabase) = db.customerDao()
}
