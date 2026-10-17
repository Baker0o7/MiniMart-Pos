package com.minimart.pos.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.minimart.pos.data.db.AppDatabase
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Real Room on an in-memory SQLite database, so DAO queries and constraints are really executed. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
abstract class DbTestBase {
    protected lateinit var db: AppDatabase

    @Before fun openDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    }

    @After fun closeDb() { db.close() }
}
