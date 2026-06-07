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
    ): AppDatabase = buildDatabase(context, callback, keyManager, retryOnFailure = true)

    private fun buildDatabase(
        context: Context,
        callback: DatabaseCallback,
        keyManager: DatabaseKeyManager,
        retryOnFailure: Boolean
    ): AppDatabase {
        return try {
            val keyBytes = keyManager.getOrCreateKey()
            val factory  = SupportFactory(keyBytes)
            keyBytes.fill(0)
            Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
                .fallbackToDestructiveMigration()
                .addCallback(callback)
                .openHelperFactory(factory)
                .build()
                .also {
                    // Force open to trigger any exception NOW rather than on first query
                    it.openHelper.writableDatabase
                }
        } catch (e: Exception) {
            android.util.Log.e("DatabaseModule", "DB open failed (${e.message}) — wiping and rebuilding", e)
            if (retryOnFailure) {
                // Wipe all DB files and retry once with a fresh encrypted DB
                listOf("", "-shm", "-wal", "-journal").forEach { suffix ->
                    context.getDatabasePath("${AppDatabase.DATABASE_NAME}$suffix").delete()
                }
                buildDatabase(context, callback, keyManager, retryOnFailure = false)
            } else {
                throw e
            }
        }
    }

    @Provides fun provideProductDao(db: AppDatabase): ProductDao = db.productDao()
    @Provides fun provideSaleDao(db: AppDatabase): SaleDao = db.saleDao()
    @Provides fun provideUserDao(db: AppDatabase): UserDao = db.userDao()
    @Provides fun provideExpenseDao(db: AppDatabase): ExpenseDao = db.expenseDao()
    @Provides @Singleton fun provideShiftDao(db: AppDatabase): ShiftDao = db.shiftDao()
    @Provides @Singleton fun provideCustomerDao(db: AppDatabase) = db.customerDao()
}
