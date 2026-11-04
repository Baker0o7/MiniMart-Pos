package com.minimart.pos.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.minimart.pos.data.db.AppDatabase
import com.minimart.pos.data.db.AppMigrations
import com.minimart.pos.data.entity.Product
import com.minimart.pos.util.BackupManager
import com.minimart.pos.util.BackupResult
import com.minimart.pos.util.DbEncryption
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Backup and restore on the real database file name, with real SQLCipher: a backup taken now puts the shop
 * back as it was, and a bad backup is refused without touching the live data.
 */
@RunWith(AndroidJUnit4::class)
class BackupRestoreInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val live get() = context.getDatabasePath(AppDatabase.DATABASE_NAME)

    private fun wipe() {
        live.parentFile?.listFiles { f -> f.name.startsWith(AppDatabase.DATABASE_NAME) }?.forEach { it.delete() }
        File(context.filesDir, "pre_restore_backup.db").delete()
        context.getSharedPreferences("minimart_dbkey", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Before fun before() = wipe()
    @After fun after() = wipe()

    private fun open(pass: ByteArray?): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DATABASE_NAME).addMigrations(*AppMigrations.ALL)
            .apply { if (pass != null) openHelperFactory(SupportOpenHelperFactory(pass)) }
            .allowMainThreadQueries().build()

    private fun count(db: AppDatabase) =
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM products").use { it.moveToFirst(); it.getInt(0) }

    private fun addProducts(n: Int, from: Int = 0) {
        val pass = DbEncryption.prepare(context)
        val db = open(pass)
        runBlocking { repeat(n) { db.productDao().insertProduct(Product(barcode = "B${from + it}", name = "Item", price = 5.0, stock = 1)) } }
        db.close()
    }

    private fun countNow(): Int {
        val db = open(DbEncryption.prepare(context)); return try { count(db) } finally { db.close() }
    }

    @Test fun aBackupPutsTheShopBackAsItWas() = runBlocking {
        addProducts(5)
        val saved = (BackupManager.backup(context) as BackupResult.Success).file
        addProducts(3, from = 100)
        assertEquals(8, countNow())

        val r = BackupManager.restore(context, saved)
        assertTrue("restore failed: $r", r is BackupResult.Success)
        assertEquals(5, countNow())
        saved.delete()
    }

    @Test fun aPassphraseBackupRestoresWithThePassphraseAndNotWithoutIt() = runBlocking {
        addProducts(4)
        val saved = (BackupManager.backup(context, passphrase = "correct horse") as BackupResult.Success).file
        addProducts(2, from = 100)

        assertTrue(BackupManager.restore(context, saved, null) is BackupResult.Error)
        assertTrue(BackupManager.restore(context, saved, "wrong") is BackupResult.Error)
        assertEquals("a refused restore must leave the shop as it was", 6, countNow())

        assertTrue(BackupManager.restore(context, saved, "correct horse") is BackupResult.Success)
        assertEquals(4, countNow())
        saved.delete()
    }

    @Test fun aDamagedBackupIsRefusedAndTheLiveDataIsUntouched() = runBlocking {
        addProducts(5)
        val saved = (BackupManager.backup(context) as BackupResult.Success).file
        val damaged = File(context.cacheDir, "damaged_backup.db")
        saved.copyTo(damaged, overwrite = true)
        damaged.writeBytes(damaged.readBytes().copyOf(damaged.length().toInt() / 2))   // cut in half

        assertTrue(BackupManager.restore(context, damaged) is BackupResult.Error)
        assertEquals(5, countNow())
        assertTrue("no half-copied staging file may be left behind",
            live.parentFile!!.listFiles { f -> f.name.endsWith(".restoring") }.isNullOrEmpty())
        damaged.delete(); saved.delete()
    }

    @Test fun aFileThatIsNotADatabaseIsRefused() = runBlocking {
        addProducts(2)
        val junk = File(context.cacheDir, "junk.db").apply { writeText("this is not a database ".repeat(400)) }
        assertTrue(BackupManager.restore(context, junk) is BackupResult.Error)
        assertEquals(2, countNow())
        junk.delete()
    }
}
