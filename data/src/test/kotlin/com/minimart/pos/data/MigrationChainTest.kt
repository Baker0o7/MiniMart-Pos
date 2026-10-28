package com.minimart.pos.data

import com.minimart.pos.data.db.AppMigrations
import org.junit.Assert.*
import org.junit.Test

/**
 * Safety net for database upgrades: a phone updating from any release since v8 must reach the current version
 * through explicit steps, never through a wipe. Fails the moment `version` is bumped without a matching migration.
 */
class MigrationChainTest : DbTestBase() {
    private val steps get() = AppMigrations.ALL.sortedBy { it.startVersion }

    @Test fun everyStepMovesExactlyOneVersion() {
        steps.forEach { assertEquals("${it.startVersion}->${it.endVersion}", it.startVersion + 1, it.endVersion) }
    }

    @Test fun stepsAreContiguousFromVersionEight() {
        assertEquals(8, steps.first().startVersion)
        steps.zipWithNext().forEach { (a, b) -> assertEquals("gap after ${a.endVersion}", a.endVersion, b.startVersion) }
    }

    @Test fun noVersionHasTwoMigrations() {
        assertEquals(steps.size, steps.map { it.startVersion }.toSet().size)
    }

    @Test fun chainEndsAtTheVersionTheAppShips() {
        val current = db.openHelper.readableDatabase.version
        assertEquals("Database version $current has no migration into it", current, steps.last().endVersion)
    }
}
