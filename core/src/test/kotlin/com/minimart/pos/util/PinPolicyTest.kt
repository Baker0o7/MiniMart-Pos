package com.minimart.pos.util

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PinPolicyTest {
    @Test fun easyPinsAreRefused() {
        listOf("1234", "4321", "0000", "1111", "999999", "123456", "654321", "7890", "0987", "1212", "4545", "2580", "112233", "6969")
            .forEach { assertNotNull("$it should be refused", PinPolicy.problem(it)) }
    }
    @Test fun reasonablePinsPass() {
        listOf("4829", "1357", "9041", "620813", "2468", "1020", "5738291").forEach { assertNull("$it should pass", PinPolicy.problem(it)) }
    }
    @Test fun lengthAndCharactersAreChecked() {
        listOf("", "123", "1234567890123", "12a4", "12 4", "٤٥٦٧").forEach { assertTrue(PinPolicy.problem(it)!!.contains("digits")) }
    }
    @Test fun wrapAroundRunsAreCaught() { assertNotNull(PinPolicy.problem("8901")); assertNotNull(PinPolicy.problem("2109")) }
    @Test fun messagesAreInPlainLanguage() { assertFalse(PinPolicy.problem("1111")!!.contains("regex")); assertTrue(PinPolicy.problem("1234")!!.contains("harder to guess")) }
}
