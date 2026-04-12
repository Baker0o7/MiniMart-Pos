package com.minimart.pos.di

import android.content.Context
import androidx.room.Room
import com.minimart.pos.data.dao.ProductDao
import com.minimart.pos.data.dao.SaleDao
import com.minimart.pos.data.dao.UserDao
import com.minimart.pos.data.db.AppDatabase
import com.minimart.pos.data.db.DatabaseCallback
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
        callback: DatabaseCallback
    ): AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
        .fallbackToDestructiveMigration()   // swap for real migrations in prod
        .addCallback(callback)
        .build()

    @Provides fun provideProductDao(db: AppDatabase): ProductDao = db.productDao()
    @Provides fun provideSaleDao(db: AppDatabase): SaleDao = db.saleDao()
    @Provides fun provideUserDao(db: AppDatabase): UserDao = db.userDao()
}
