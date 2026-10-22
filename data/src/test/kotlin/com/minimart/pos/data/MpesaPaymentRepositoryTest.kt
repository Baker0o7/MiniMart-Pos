package com.minimart.pos.data

import com.minimart.pos.data.entity.MpesaPayment
import com.minimart.pos.data.repository.MpesaPaymentRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class MpesaPaymentRepositoryTest : DbTestBase() {
    private val repo get() = MpesaPaymentRepository(db.mpesaPaymentDao())
    private fun pay(code: String, amount: Double = 100.0, at: Long = 1_000) =
        MpesaPayment(code = code, amount = amount, senderName = "Jane", senderPhone = "0712", receivedAt = at, rawMessage = "raw")

    @Test fun aRepeatedCodeIsStoredOnce() = runBlocking {
        assertTrue(repo.record(pay("ABC123XYZ9")))
        assertFalse(repo.record(pay("ABC123XYZ9", amount = 999.0)))   // an SMS and a webhook can both report it
        assertEquals(100.0, repo.findByCode("ABC123XYZ9")!!.amount, 1e-9)
    }

    @Test fun lookupIgnoresCaseAndSpaces() = runBlocking {
        repo.record(pay("QWE456RTY7"))
        assertNotNull(repo.findByCode("  qwe456rty7 "))
        assertNull(repo.findByCode("NOPE000000"))
    }

    @Test fun rangesIncludeTheStartAndExcludeTheEnd() = runBlocking {
        repo.record(pay("A00000001A", at = 1_000)); repo.record(pay("A00000002A", at = 2_000)); repo.record(pay("A00000003A", at = 3_000))
        assertEquals(listOf("A00000002A", "A00000001A"), repo.between(1_000, 3_000).map { it.code })
        assertEquals(3, repo.getByRange(0, 3_000).first().size)   // the flow version includes the end
    }

    @Test fun totalsCountOnlyPaymentsSinceTheStart() = runBlocking {
        repo.record(pay("B00000001B", amount = 50.0, at = 500)); repo.record(pay("B00000002B", amount = 70.0, at = 1_500))
        assertEquals(70.0, repo.totalSince(1_000).first(), 1e-9)
        assertEquals(1, repo.countSince(1_000).first())
    }
}
