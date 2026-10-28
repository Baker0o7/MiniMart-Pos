package com.minimart.pos.data

import com.minimart.pos.data.repository.MpesaInboxSync
import com.minimart.pos.data.repository.MpesaPaymentRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Safaricom retries, the cloud re-sends until acknowledged, phones stop half way: none of it may double-count money. */
class MpesaInboxSyncTest : DbTestBase() {
    private val repo get() = MpesaPaymentRepository(db.mpesaPaymentDao())
    private val sync get() = MpesaInboxSync(repo)
    private val now = 1_800_000_000_000L

    private fun row(id: Long, code: String, amount: Double = 100.0) =
        """{"id":$id,"code":"$code","amount":$amount,"name":"Jane Doe","phone":"0712345678","paidAtMs":${now - 1000}}"""
    private fun batch(vararg rows: String) = rows.joinToString(",", "[", "]")
    private suspend fun total() = repo.totalSince(0).first()

    @Test fun aNewPaymentIsStoredAndAcknowledged() = runBlocking {
        val o = sync.process(batch(row(1, "QGH7XYZ123")), now)
        assertEquals(1, o.recorded); assertEquals(0, o.duplicates); assertEquals(listOf(1L), o.ackIds)
        assertEquals(100.0, total(), 1e-9)
    }

    @Test fun theSameBatchTwiceChangesNothingTheSecondTime() = runBlocking {
        val b = batch(row(1, "QGH7XYZ123"), row(2, "QGH7XYZ124", 250.0))
        val first = sync.process(b, now); val second = sync.process(b, now)
        assertEquals(2, first.recorded)
        assertEquals(0, second.recorded); assertEquals(2, second.duplicates)
        assertEquals(listOf(1L, 2L), second.ackIds)           // still acknowledged, so the cloud stops re-sending
        assertEquals(350.0, total(), 1e-9)
    }

    @Test fun theSameCodeInDifferentCaseAndSpacingIsOnePayment() = runBlocking {
        sync.process(batch(row(1, "QGH7XYZ123")), now)
        val o = sync.process(batch(row(2, " qgh7xyz123 ")), now)
        assertEquals(1, o.duplicates); assertEquals(100.0, total(), 1e-9)
    }

    @Test fun aRepeatInsideOneBatchIsCountedOnce() = runBlocking {
        val o = sync.process(batch(row(1, "QGH7XYZ123"), row(2, "QGH7XYZ123", 999.0)), now)
        assertEquals(1, o.recorded); assertEquals(1, o.duplicates)
        assertEquals(100.0, total(), 1e-9)                       // the first amount wins; a replay cannot change it
    }

    @Test fun anSmsAndAWebhookForTheSamePaymentAreOne() = runBlocking {
        repo.record(com.minimart.pos.data.entity.MpesaPayment(code = "qgh7xyz123", amount = 100.0, senderName = "J", senderPhone = "", receivedAt = 5, rawMessage = "sms"))
        val o = sync.process(batch(row(1, "QGH7XYZ123")), now)
        assertEquals(0, o.recorded); assertEquals(1, o.duplicates)
        assertEquals(1, repo.countSince(0).first())
    }

    @Test fun invalidRowsAreAcknowledgedButNeverStored() = runBlocking {
        val o = sync.process(batch(row(1, "bad!"), row(2, "QGH7XYZ123", -5.0), row(3, "QGH7XYZ125")), now)
        assertEquals(1, o.recorded); assertEquals(2, o.rejected); assertEquals(listOf(1L, 2L, 3L), o.ackIds)
    }

    @Test fun malformedRowsAreSkippedWithoutAcknowledgingGuesses() = runBlocking {
        val o = sync.process("""[1, {"code":"QGH7XYZ123","amount":100}, null, ${row(7, "QGH7XYZ126")}]""", now)
        assertEquals(listOf(7L), o.ackIds); assertEquals(1, o.recorded)
    }

    @Test fun anEmptyBatchDoesNothing() = runBlocking {
        val o = sync.process("[]", now)
        assertEquals(0, o.recorded + o.duplicates + o.rejected); assertTrue(o.ackIds.isEmpty())
    }
}
