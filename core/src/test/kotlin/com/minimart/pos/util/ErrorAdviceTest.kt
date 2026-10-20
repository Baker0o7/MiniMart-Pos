package com.minimart.pos.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.FileNotFoundException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class SQLiteFullException(message: String) : RuntimeException(message)

class ErrorAdviceTest {
    @Test fun storageFullTellsThemToFreeSpace() {
        assertTrue(ErrorAdvice.reason(SQLiteFullException("database or disk is full")).contains("storage is full"))
        assertTrue(ErrorAdvice.reason(IOException("No space left on device")).contains("storage is full"))
    }

    @Test fun walksThroughTheCauseChain() {
        val wrapped = RuntimeException("save failed", UnknownHostException("x"))
        assertTrue(ErrorAdvice.reason(wrapped).contains("Wi-Fi"))
    }

    @Test fun timeoutsAndMissingFilesAreExplained() {
        assertTrue(ErrorAdvice.reason(SocketTimeoutException()).contains("too long"))
        assertTrue(ErrorAdvice.reason(FileNotFoundException("a.db")).contains("not found"))
    }

    @Test fun ourOwnMessagesPassThrough() {
        assertEquals("Add at least one item first.", ErrorAdvice.reason(IllegalStateException("Add at least one item first")))
    }

    @Test fun unknownErrorsGetASafeNextStep() {
        assertTrue(ErrorAdvice.reason(RuntimeException()).contains("restart the app"))
    }

    @Test fun explainNamesTheAction() {
        assertTrue(ErrorAdvice.explain(IOException("boom"), "Saving the backup").startsWith("Saving the backup didn't work."))
    }
}
