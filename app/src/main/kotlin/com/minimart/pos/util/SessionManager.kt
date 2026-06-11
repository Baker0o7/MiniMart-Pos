package com.minimart.pos.util

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionManager @Inject constructor() {

    companion object {
        private const val TAG = "SessionManager"
        // Timeout in ms — 15 minutes of inactivity
        private const val INACTIVITY_TIMEOUT_MS = 15 * 60 * 1000L
    }

    private val _isExpired = MutableStateFlow(false)
    val isExpired: StateFlow<Boolean> = _isExpired

    private var lastActivityMs = System.currentTimeMillis()

    /** Call on every user interaction (tap, swipe, keypress) */
    fun recordActivity() {
        lastActivityMs = System.currentTimeMillis()
        if (_isExpired.value) _isExpired.value = false
    }

    /** Check if the session has expired — call periodically */
    fun checkExpiry() {
        val idle = System.currentTimeMillis() - lastActivityMs
        if (idle >= INACTIVITY_TIMEOUT_MS && !_isExpired.value) {
            Log.i(TAG, "Session expired after ${idle / 1000}s idle")
            _isExpired.value = true
        }
    }

    fun reset() {
        lastActivityMs = System.currentTimeMillis()
        _isExpired.value = false
    }
}
