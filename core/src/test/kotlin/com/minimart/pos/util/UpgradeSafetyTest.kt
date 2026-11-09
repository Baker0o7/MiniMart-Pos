package com.minimart.pos.util

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UpgradeSafetyTest {
    private val mb = 1_000_000L

    @Test fun `copies once when a newer version first runs`() = assertTrue(UpgradeSafety.shouldCopy(137, 138, 20 * mb, 500 * mb))
    @Test fun `no copy on a fresh install`() = assertFalse(UpgradeSafety.shouldCopy(0, 138, 0, 500 * mb))
    @Test fun `no copy when the version did not change`() = assertFalse(UpgradeSafety.shouldCopy(138, 138, 20 * mb, 500 * mb))
    @Test fun `no copy when going back to an older build`() = assertFalse(UpgradeSafety.shouldCopy(139, 138, 20 * mb, 500 * mb))
    @Test fun `no copy when the phone is nearly full`() = assertFalse(UpgradeSafety.shouldCopy(137, 138, 20 * mb, 30 * mb))
    @Test fun `no copy of an empty database`() = assertFalse(UpgradeSafety.shouldCopy(137, 138, 0, 500 * mb))
}
