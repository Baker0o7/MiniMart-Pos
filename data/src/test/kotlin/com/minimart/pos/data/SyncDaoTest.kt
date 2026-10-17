package com.minimart.pos.data

import com.minimart.pos.data.entity.SyncEntityType
import com.minimart.pos.data.entity.SyncLog
import com.minimart.pos.data.entity.SyncOperation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SyncDaoTest : DbTestBase() {
    private fun log(device: String = "till-1", entityId: Long = 7, op: SyncOperation = SyncOperation.CREATE, at: Long = 1_000L) =
        SyncLog(entityType = SyncEntityType.SALE, entityId = entityId, operation = op, deviceId = device, payload = "{}", createdAt = at)

    @Test fun theSameChangeIsRecordedOnlyOnce() = runBlocking {
        assertNotNull(db.syncDao().insertLogIfNew(log()))
        assertNull(db.syncDao().insertLogIfNew(log()))   // a retry after a dropped connection
        assertEquals(1, db.syncDao().getPendingLogs().size + db.syncDao().countMatchingLog("till-1", SyncEntityType.SALE, 7, SyncOperation.CREATE, 1_000L) - 1)
    }

    @Test fun changesThatDifferInAnyKeyFieldAreAllKept() = runBlocking {
        assertNotNull(db.syncDao().insertLogIfNew(log()))
        assertNotNull(db.syncDao().insertLogIfNew(log(device = "till-2")))
        assertNotNull(db.syncDao().insertLogIfNew(log(entityId = 8)))
        assertNotNull(db.syncDao().insertLogIfNew(log(op = SyncOperation.UPDATE)))
        assertNotNull(db.syncDao().insertLogIfNew(log(at = 2_000L)))
    }
}
