package com.minimart.pos.data

import app.cash.turbine.test
import com.minimart.pos.data.entity.MpesaPayment
import com.minimart.pos.data.repository.MpesaPaymentRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Turbine checks what a screen actually sees over time: each emission, in order, and nothing extra. */
class MpesaFlowTurbineTest : DbTestBase() {
    private val repo get() = MpesaPaymentRepository(db.mpesaPaymentDao())
    private fun pay(code: String, amount: Double) =
        MpesaPayment(code = code, amount = amount, senderName = "Jane", senderPhone = "0712345678", receivedAt = 1_000L, rawMessage = "")

    @Test fun totalFollowsNewPaymentsAndIgnoresDuplicates() = runBlocking {
        repo.totalSince(0).test {
            assertEquals(0.0, awaitItem(), 1e-9)
            repo.record(pay("QGH7XYZ123", 100.0))
            assertEquals(100.0, awaitItem(), 1e-9)
            assertFalse(repo.record(pay("qgh7xyz123", 100.0)))   // a repeat changes nothing, so no new value is sent
            repo.record(pay("QGH7XYZ124", 50.0))
            assertEquals(150.0, awaitItem(), 1e-9)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun countFollowsNewPayments() = runBlocking {
        repo.countSince(0).test {
            assertEquals(0, awaitItem())
            repo.record(pay("QGH7XYZ125", 10.0))
            assertEquals(1, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
