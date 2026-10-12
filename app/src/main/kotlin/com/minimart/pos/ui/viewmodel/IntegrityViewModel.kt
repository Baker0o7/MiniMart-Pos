package com.minimart.pos.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minimart.pos.data.repository.IntegrityChecker
import com.minimart.pos.data.repository.IntegrityReport
import com.minimart.pos.util.AuditEvent
import com.minimart.pos.util.AuditLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Runs the read-only data check from Settings and shows what it found. */
@HiltViewModel
class IntegrityViewModel @Inject constructor(
    private val checker: IntegrityChecker,
    private val audit: AuditLogger
) : ViewModel() {
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running
    private val _report = MutableStateFlow<IntegrityReport?>(null)
    val report: StateFlow<IntegrityReport?> = _report
    private val _failed = MutableStateFlow<String?>(null)
    val failed: StateFlow<String?> = _failed

    fun run() {
        if (_running.value) return
        _running.value = true
        _failed.value = null
        viewModelScope.launch {
            try {
                val r = withContext(Dispatchers.IO) { checker.check() }
                _report.value = r
                runCatching { audit.log(AuditEvent.DATA_CHECK,
                    detail = if (r.ok) "Manual check: ${r.salesChecked} sales, no problems"
                             else "Manual check: ${r.issues.size} problem(s) — ${r.issues.take(3).joinToString("; ")}") }
            } catch (e: Exception) {
                _failed.value = e.message ?: "Check could not run"
            } finally { _running.value = false }
        }
    }

    /** Manual backups and restores go in the activity log; flushed because a restore restarts the app. */
    fun recordBackup(event: AuditEvent, detail: String) {
        audit.log(event, detail = detail)
        viewModelScope.launch(Dispatchers.IO) { audit.flush() }
    }

    fun dismiss() { _report.value = null; _failed.value = null }
}
