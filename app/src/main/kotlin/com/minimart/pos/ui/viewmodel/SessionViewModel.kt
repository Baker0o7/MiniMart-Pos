package com.minimart.pos.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minimart.pos.util.AuditLogger
import com.minimart.pos.util.AuditEvent
import com.minimart.pos.util.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SessionViewModel @Inject constructor(
    val sessionManager: SessionManager,
    val auditLogger: AuditLogger
) : ViewModel() {

    init {
        // Check session expiry every 60 seconds
        viewModelScope.launch {
            while (true) {
                delay(60_000)
                sessionManager.checkExpiry()
            }
        }
    }

    fun onUserActivity() = sessionManager.recordActivity()

    fun onSessionExpired(username: String) {
        auditLogger.log(AuditEvent.SESSION_EXPIRED, user = username, detail = "Auto-logout after 15min idle")
        sessionManager.reset()
    }
}
