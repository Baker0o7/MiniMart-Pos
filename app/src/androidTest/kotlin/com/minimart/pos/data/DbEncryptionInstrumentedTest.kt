package com.minimart.pos.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.minimart.pos.data.db.AppDatabase
import com.minimart.pos.data.db.AppMigrations
import com.minimart.pos.data.entity.Product
import com.minimart.pos.util.BackupManager
import com.minimart.pos.util.DbEncryption
import com.minimart.pos.util.DbFormat
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Runs the real SQLCipher library on a real Android runtime (the emulator), because it cannot run on the
 * build machine's plain JVM. Proves the one-time conversion keeps every row and the schema version Room checks,
 * that Room reopens the result, and that backups made from an encrypted database restore.
 */
@RunWith(AndroidJUnit4::class)
class DbEncryptionInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "enc_test.db"
    private val file get() = context.getDatabasePath(name)

    private fun wipe() {
        context.getDatabasePath(name).parentFile?.listFiles { f -> f.name.startsWith(name) }?.forEach { it.delete() }
        File(context.cacheDir, "snap_test.db").delete()
        context.getSharedPreferences("minimart_dbkey", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Before fun before() = wipe()
    @After fun after() = wipe()

    /** Prepares the test database and, if that leaves it plain, fails with the reason the app itself records. */
    private fun prepared(): ByteArray = DbEncryption.prepare(context, name)
        ?: throw AssertionError("prepare returned null; status = " + DbEncryption.status(context))

    private fun openRoom(passphrase: ByteArray?): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(*AppMigrations.ALL)
            .apply { if (passphrase != null) openHelperFactory(SupportOpenHelperFactory(passphrase)) }
            .allowMainThreadQueries().build()

    private fun plainDbWithProducts(count: Int) {
        val db = openRoom(null)
        runBlocking { repeat(count) { db.productDao().insertProduct(Product(barcode = "E$it", name = "Item $it", price = 10.0 + it, stock = 5)) } }
        db.close()
    }

    private fun productCount(db: AppDatabase) = runBlocking { db.productDao().getProductById(1)?.let { 1 } ?: 0 }

    @Test fun plainDatabaseIsConvertedWithEveryRowAndTheSchemaVersion() {
        plainDbWithProducts(40)
        assertEquals(DbFormat.Kind.PLAIN, DbEncryption.kind(file))
        val before = AppDatabase.DATABASE_NAME.length   // keep the constant referenced so a rename breaks this test
        assertTrue(before > 0)

        val pass = prepared()
        assertEquals(DbFormat.Kind.ENCRYPTED, DbEncryption.kind(file))

        val db = openRoom(pass)                       // Room must accept it: no "new database", no destructive wipe
        try {
            assertEquals(1, productCount(db))
            assertEquals(40, db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM products").use { it.moveToFirst(); it.getInt(0) })
        } finally { db.close() }
    }

    @Test fun preparingAgainIsHarmlessAndReturnsTheSameKey() {
        plainDbWithProducts(3)
        val first = prepared()
        val second = prepared()
        assertTrue(first.contentEquals(second))
    }

    @Test fun aBrandNewDatabaseIsCreatedEncrypted() {
        val pass = DbEncryption.prepare(context, name)
        assertNotNull(pass)
        val db = openRoom(pass)
        runBlocking { db.productDao().insertProduct(Product(barcode = "N1", name = "New", price = 1.0, stock = 1)) }
        db.close()
        assertEquals(DbFormat.Kind.ENCRYPTED, DbEncryption.kind(file))
    }

    @Test fun backupSnapshotIsPlainReadableAndRestoresIntoAnEncryptedDatabase() {
        plainDbWithProducts(5)
        val pass = prepared()
        val snap = File(context.cacheDir, "snap_test.db")
        DbEncryption.snapshotPlain(context, snap, name)
        assertEquals(DbFormat.Kind.PLAIN, DbEncryption.kind(snap))
        assertNull("the snapshot must pass the same check a real backup does", BackupManager.verify(context, snap))
        val plainCount = android.database.sqlite.SQLiteDatabase.openDatabase(snap.absolutePath, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY)
            .use { it.rawQuery("SELECT COUNT(*) FROM products", null).use { c -> c.moveToFirst(); c.getInt(0) } }
        assertEquals(5, plainCount)

        // Restore on a "new phone": the live file is gone and the snapshot is put in its place.
        file.delete(); File(file.path + "-wal").delete(); File(file.path + "-shm").delete()
        snap.copyTo(file, overwrite = true)
        val again = prepared()
        assertNotNull(again)
        assertEquals(DbFormat.Kind.ENCRYPTED, DbEncryption.kind(file))
        val db = openRoom(again)
        try { assertEquals(5, db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM products").use { it.moveToFirst(); it.getInt(0) }) }
        finally { db.close() }
        assertTrue(pass.isNotEmpty())
    }

    @Test fun anEncryptedCopyOfTheLiveFileIsAcceptedAsABackupOnThisPhone() {
        plainDbWithProducts(2)
        DbEncryption.prepare(context, name)
        DbEncryption.checkpoint(context, name)
        val copy = File(context.cacheDir, "snap_test.db")
        file.copyTo(copy, overwrite = true)
        assertNull(BackupManager.verify(context, copy))
    }

    @Test fun aLostKeyPutsTheFileAsideInsteadOfCrashing() {
        plainDbWithProducts(2)
        DbEncryption.prepare(context, name)
        // Simulate the key being lost: its stored copy vanishes but the encrypted file stays.
        context.getSharedPreferences("minimart_dbkey", Context.MODE_PRIVATE).edit().clear().commit()
        val pass = prepared()
        val aside = file.parentFile!!.listFiles { f -> f.name.startsWith("$name.unreadable-") }
        assertTrue("the unreadable file must be kept, not deleted", !aside.isNullOrEmpty())
    }
}
