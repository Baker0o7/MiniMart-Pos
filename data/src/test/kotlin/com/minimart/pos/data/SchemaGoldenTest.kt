package com.minimart.pos.data

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.minimart.pos.data.db.AppMigrations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Two safety nets for database upgrades, both built on a saved copy of the schema ("golden", one line per table or index)
 * kept in `src/test/resources/schema/v<N>.sql`:
 *
 * 1. The schema Room builds now must equal the golden file for the current version, so changing an entity without
 *    bumping the version (or bumping without saving the new golden file) fails here.
 * 2. Every saved older schema is upgraded through the real migrations and must end up identical to the current one,
 *    which is exactly what a phone updating from that release goes through.
 *
 * When you bump the database version: add `MIGRATION_n_m`, then save the new `v<m>.sql` (the failing test prints it).
 * Keep the old files: they are what makes the upgrade test possible.
 */
class SchemaGoldenTest : DbTestBase() {

    private fun normalise(sql: String) = sql.replace(Regex("\\s+"), " ").replace("( ", "(").replace(" )", ")").trim()

    /** One normalised line per table/index, sorted, ignoring Room's and Android's bookkeeping tables. */
    private fun dump(d: SupportSQLiteDatabase): List<String> =
        d.query("SELECT type, name, sql FROM sqlite_master WHERE sql IS NOT NULL AND name NOT LIKE 'sqlite_%' " +
            "AND name NOT IN ('room_master_table', 'android_metadata')").use { c ->
            generateSequence { if (c.moveToNext()) "${c.getString(0)} ${c.getString(1)}: ${normalise(c.getString(2))}" else null }.toList()
        }.sorted()

    /**
     * What an upgraded phone and a new install must agree on: every table's columns (name, type, null, default, key),
     * its foreign keys, and every index. Not the CREATE text itself: `ALTER TABLE ... ADD COLUMN` writes the new column
     * after the table's constraints while a fresh install writes it among the columns, which changes nothing real.
     */
    private fun structure(d: SupportSQLiteDatabase): List<String> {
        fun rows(sql: String, cols: List<Int>) = d.query(sql).use { c ->
            generateSequence { if (c.moveToNext()) cols.joinToString("|") { i -> c.getString(i) ?: "" } else null }.toList()
        }
        val tables = d.query("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' " +
            "AND name NOT IN ('room_master_table', 'android_metadata')").use { c ->
            generateSequence { if (c.moveToNext()) c.getString(0) else null }.toList()
        }
        val out = tables.map { t ->
            "table $t cols=" + rows("PRAGMA table_info(`$t`)", listOf(1, 2, 3, 4, 5)).joinToString(";") +
                " fks=" + rows("PRAGMA foreign_key_list(`$t`)", listOf(2, 3, 4, 5, 6)).sorted().joinToString(";")
        }
        return (out + dump(d).filter { it.startsWith("index ") }).sorted()
    }

    private fun golden(version: Int): List<String>? =
        javaClass.getResourceAsStream("/schema/v$version.sql")?.bufferedReader(Charsets.UTF_8)?.readLines()
            ?.map { it.trim() }?.filter { it.isNotEmpty() && !it.startsWith("--") }

    private fun bareDb(version: Int, lines: List<String>): Pair<SupportSQLiteOpenHelper, SupportSQLiteDatabase> {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val helper = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(ctx).name(null)
                .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        // Tables first, then indexes, because an index needs its table.
                        lines.sortedBy { if (it.startsWith("table ")) 0 else 1 }.forEach { db.execSQL(it.substringAfter(": ")) }
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, o: Int, n: Int) {}
                }).build())
        return helper to helper.writableDatabase
    }

    private val currentVersion get() = AppMigrations.ALL.maxOf { it.endVersion }

    @Test fun currentSchemaMatchesTheSavedCopy() {
        val actual = dump(db.openHelper.writableDatabase)
        // Also written next to the other build outputs, so CI can print it when the saved copy is missing or stale.
        runCatching { java.io.File("build/schema-actual.txt").apply { parentFile?.mkdirs() }.writeText(actual.joinToString("\n") + "\n") }
        val saved = golden(currentVersion)
        assertNotNull("Save the schema below as data/src/test/resources/schema/v$currentVersion.sql:\n" + actual.joinToString("\n"), saved)
        assertEquals("The database no longer matches schema v$currentVersion. Bump the version, add a migration and save a new golden file.\n" +
            "Actual schema:\n" + actual.joinToString("\n"), saved!!.sorted(), actual)
    }

    @Test fun everySavedOlderSchemaUpgradesToTheCurrentOne() {
        val current = structure(db.openHelper.writableDatabase)
        val saved = (8 until currentVersion).mapNotNull { v -> golden(v)?.let { v to it } }
        saved.forEach { (v, lines) ->
            val (helper, sdb) = bareDb(v, lines)
            AppMigrations.ALL.filter { it.startVersion >= v }.sortedBy { it.startVersion }.forEach { it.migrate(sdb) }
            assertEquals("Upgrading a phone from v$v gives a different schema than a new install", current, structure(sdb))
            helper.close()
        }
    }
}
