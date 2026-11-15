package com.minimart.pos.sync

/**
 * Sync talks plain HTTP, so it must only ever go to a phone on the shop's own network. Accepting only
 * private IPv4 literals also rules out host names and tricks like "192.168.1.5@example.com".
 */
object LanAddress {
    private val IPV4 = Regex("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$")

    fun isPrivate(host: String): Boolean {
        val m = IPV4.matchEntire(host.trim()) ?: return false
        val o = m.groupValues.drop(1).map { it.toIntOrNull() ?: return false }
        if (o.any { it !in 0..255 }) return false
        return o[0] == 10 ||
            (o[0] == 172 && o[1] in 16..31) ||
            (o[0] == 192 && o[1] == 168) ||
            (o[0] == 169 && o[1] == 254) ||
            o[0] == 127
    }
}
