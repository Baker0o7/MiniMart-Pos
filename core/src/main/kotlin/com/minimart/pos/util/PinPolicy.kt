package com.minimart.pos.util

/**
 * Which PINs are too easy to guess. A shop till has a short numeric PIN, so the only real protection is not
 * choosing the first thing an attacker would try: repeated digits, counting up or down, a repeated pair, and a
 * short list of the PINs people choose most. Used wherever a PIN is created or changed. Pure, so it is tested.
 */
object PinPolicy {
    const val MIN = 4
    const val MAX = 12
    private val COMMON = setOf("1234", "0000", "1111", "2580", "1122", "1212", "1004", "2000", "2001", "6969", "4321", "1313", "123456", "654321", "112233", "121212", "000000")

    /** A plain-language reason the PIN is not allowed, or null when it is fine. */
    fun problem(pin: String): String? {
        if (pin.length < MIN || pin.length > MAX || !pin.all { it in '0'..'9' }) return "PIN must be $MIN to $MAX digits"
        if (pin.all { it == pin[0] }) return "Do not repeat one digit. Choose a PIN that is harder to guess"
        if (isRun(pin)) return "Do not use digits in a row like 1234 or 9876. Choose a PIN that is harder to guess"
        if (pin.length % 2 == 0 && pin.chunked(2).all { it == pin.take(2) }) return "Do not repeat a pair of digits. Choose a PIN that is harder to guess"
        if (pin in COMMON) return "That PIN is too common. Choose a PIN that is harder to guess"
        return null
    }

    private fun isRun(p: String): Boolean {
        val up = p.zipWithNext().all { (a, b) -> b - a == 1 || (a == '9' && b == '0') }
        val down = p.zipWithNext().all { (a, b) -> a - b == 1 || (a == '0' && b == '9') }
        return up || down
    }
}
