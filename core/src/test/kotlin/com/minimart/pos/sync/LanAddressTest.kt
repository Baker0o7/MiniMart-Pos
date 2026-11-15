package com.minimart.pos.sync

import org.junit.Assert.*
import org.junit.Test

class LanAddressTest {
    @Test fun acceptsPrivateRanges() {
        listOf("192.168.1.20", "192.168.43.1", "10.0.0.5", "172.16.0.1", "172.31.255.254", "172.20.10.2", " 192.168.0.9 ")
            .forEach { assertTrue(it, LanAddress.isPrivate(it)) }
    }
    @Test fun rejectsPublicAndMalformed() {
        listOf("8.8.8.8", "172.32.0.1", "172.15.0.1", "192.169.1.1", "example.com", "192.168.1.5@evil.com",
            "192.168.1.5/x", "192.168.1", "192.168.1.256", "192.168.1.5:80", "", "999.1.1.1", "192.168.1.5.9")
            .forEach { assertFalse(it, LanAddress.isPrivate(it)) }
    }
}
