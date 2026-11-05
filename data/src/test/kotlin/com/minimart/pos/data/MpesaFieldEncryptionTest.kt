package com.minimart.pos.data

import com.minimart.pos.data.entity.MpesaPayment
import com.minimart.pos.data.repository.MpesaPaymentRepository
import com.minimart.pos.util.AesGcmBox
import com.minimart.pos.util.FieldCipher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.KeyGenerator

/** Who paid is sealed in the database file, readable through the repository, and old rows get sealed once. */
class MpesaFieldEncryptionTest : DbTestBase() {
    private fun key() = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val cipher = FieldCipher(AesGcmBox(key()))
    private val repo by lazy { MpesaPaymentRepository(db.mpesaPaymentDao(), cipher) }

    private fun pay(code: String = "QWE123ABCD") = MpesaPayment(code = code, amount = 500.0, senderName = "JOHN OTIENO",
        senderPhone = "0712345678", receivedAt = 1_000L, rawMessage = "$code Confirmed. Ksh500.00 from JOHN OTIENO 0712345678")

    private fun rawRow(code: String) = db.openHelper.readableDatabase.query(
        "SELECT senderName, senderPhone, rawMessage, amount FROM mpesa_payments WHERE code = '$code'").use { c ->
        c.moveToFirst(); listOf(c.getString(0), c.getString(1), c.getString(2), c.getString(3))
    }

    @Test fun theDatabaseFileHoldsNoNamePhoneOrMessageText() = runBlocking {
        repo.record(pay())
        val raw = rawRow("QWE123ABCD")
        raw.take(3).forEach { assertTrue(it.startsWith("enc1:")); assertFalse(it.contains("OTIENO") || it.contains("0712345678")) }
        assertEquals("500.0", raw[3])
    }

    @Test fun theRepositoryGivesTheDetailsBackInEveryWayToReadThem() = runBlocking {
        repo.record(pay())
        for (p in listOf(repo.findByCode("qwe123abcd")!!, repo.between(0, 2_000).single(), repo.getByRange(0, 2_000).first().single())) {
            assertEquals("JOHN OTIENO", p.senderName); assertEquals("0712345678", p.senderPhone)
            assertTrue(p.rawMessage.contains("0712345678")); assertEquals(500.0, p.amount, 0.0)
        }
    }

    @Test fun aRepeatedCodeIsStillRefusedAndTotalsStillWork() = runBlocking {
        assertTrue(repo.record(pay())); assertFalse(repo.record(pay().copy(senderName = "SOMEONE ELSE")))
        assertEquals(500.0, repo.totalSince(0).first(), 0.0)
    }

    @Test fun rowsFromBeforeEncryptionStillReadAndAreSealedOnceWithoutLosingAnything() = runBlocking {
        db.mpesaPaymentDao().insert(pay("OLD111AAAA"))          // saved the old way, readable
        db.mpesaPaymentDao().insert(pay("OLD222BBBB").copy(senderPhone = ""))
        assertEquals("JOHN OTIENO", repo.findByCode("OLD111AAAA")!!.senderName)
        assertEquals(2, repo.sealLegacyRows())
        assertEquals(0, repo.sealLegacyRows())                  // nothing left, safe to repeat
        assertTrue(rawRow("OLD111AAAA").take(3).all { it.startsWith("enc1:") })
        assertEquals("", rawRow("OLD222BBBB")[1])               // "no phone" stays empty
        val back = repo.findByCode("OLD111AAAA")!!
        assertEquals("0712345678", back.senderPhone); assertEquals(500.0, back.amount, 0.0)
    }

    @Test fun anotherPhonesKeyShowsNoDetailsButTheMoneyRecordIsIntact() = runBlocking {
        repo.record(pay())
        val other = MpesaPaymentRepository(db.mpesaPaymentDao(), FieldCipher(AesGcmBox(key())))
        val p = other.findByCode("QWE123ABCD")!!
        assertEquals("", p.senderName); assertEquals("", p.senderPhone)
        assertEquals("QWE123ABCD", p.code); assertEquals(500.0, p.amount, 0.0)
    }

    @Test fun withoutAKeyTheAppStillWorksAndStoresAsBefore() = runBlocking {
        val plain = MpesaPaymentRepository(db.mpesaPaymentDao())
        plain.record(pay())
        assertEquals("JOHN OTIENO", rawRow("QWE123ABCD")[0])
        assertEquals("JOHN OTIENO", plain.findByCode("QWE123ABCD")!!.senderName)
        assertEquals(0, plain.sealLegacyRows())
    }
}
