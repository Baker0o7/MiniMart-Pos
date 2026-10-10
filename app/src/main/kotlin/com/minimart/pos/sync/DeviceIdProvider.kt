package com.minimart.pos.sync

import android.content.SharedPreferences
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** This device's sync identity and whether it has been paired with a main device. */
@Singleton
class DeviceIdProvider @Inject constructor(private val prefs: SharedPreferences) {
    fun id(): String = prefs.getString("device_id", null)
        ?: UUID.randomUUID().toString().also { prefs.edit().putString("device_id", it).apply() }

    /** A device with a main-device address saved is a till: its sales are queued to be sent. */
    fun isPaired(): Boolean = !prefs.getString("peer_ip", "").isNullOrBlank()
}
