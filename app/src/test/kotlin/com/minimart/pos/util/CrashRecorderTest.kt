package com.minimart.pos.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class CrashRecorderTest {
    @Test fun reportNamesTheVersionDeviceAndCause() {
        val text = CrashRecorder.format(1_800_000_000_000L, "1.16.33", "Tecno Spark", 33, "main", IllegalStateException("boom"))
        assertTrue(text.contains("App version: 1.16.33"))
        assertTrue(text.contains("Tecno Spark (Android SDK 33)"))
        assertTrue(text.contains("IllegalStateException: boom"))
        assertTrue(text.contains("Thread: main"))
    }

    @Test fun anEnormousTraceIsCapped() {
        val deep = RuntimeException("x".repeat(100_000))
        assertTrue(CrashRecorder.format(0, "v", "m", 1, "t", deep).length < 21_000)
    }

    @Test fun onlyTheNewestReportsAreKept() {
        val dir = Files.createTempDirectory("crashes").toFile()
        try {
            (1L..8L).forEach { File(dir, "crash_${it * 1000}.txt").writeText("r$it") }
            File(dir, "notes.txt").writeText("not a report")
            CrashRecorder.prune(dir, 5)
            val kept = dir.listFiles()!!.map { it.name }.sorted()
            assertEquals(listOf("crash_4000.txt", "crash_5000.txt", "crash_6000.txt", "crash_7000.txt", "crash_8000.txt", "notes.txt"), kept)
        } finally { dir.deleteRecursively() }
    }

    @Test fun timestampIsReadFromTheFileName() {
        assertEquals(1234L, CrashRecorder.timestampOf(File("crash_1234.txt")))
        assertEquals(0L, CrashRecorder.timestampOf(File("crash_abc.txt")))
    }
}
