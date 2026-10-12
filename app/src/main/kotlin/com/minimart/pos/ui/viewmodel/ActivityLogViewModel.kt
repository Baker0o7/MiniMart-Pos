package com.minimart.pos.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minimart.pos.util.AuditCheck
import com.minimart.pos.util.AuditLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** One parsed line of the activity log. */
data class ActivityEntry(val time: String, val event: String, val user: String, val detail: String)

enum class ActivityFilter(val label: String, val events: Set<String>?) {
    ALL("All", null),
    SALES("Sales", setOf("SALE_COMPLETED", "DISCOUNT_APPLIED", "CREDIT_USED", "CREDIT_ADDED", "NO_SALE")),
    REVERSALS("Voids & refunds", setOf("SALE_VOIDED", "SALE_REFUNDED")),
    STOCK("Stock & prices", setOf("PRODUCT_CREATED", "PRODUCT_EDITED", "PRODUCT_DELETED", "STOCK_ADJUSTED")),
    SHIFTS("Shifts", setOf("SHIFT_OPENED", "SHIFT_CLOSED")),
    SECURITY("Sign-ins & staff", setOf("LOGIN_SUCCESS", "LOGIN_FAILED", "LOGOUT", "SESSION_EXPIRED", "USER_CREATED", "USER_DELETED", "PIN_CHANGED")),
    SYSTEM("Backups", setOf("BACKUP_CREATED", "BACKUP_FAILED", "BACKUP_RESTORED", "DATA_CHECK", "PERMISSION_DENIED", "LOG_ROTATED", "SETTINGS_CHANGED"))
}

data class ActivityUiState(
    val loading: Boolean = true,
    val entries: List<ActivityEntry> = emptyList(),
    val filter: ActivityFilter = ActivityFilter.ALL,
    val check: AuditCheck? = null
) {
    val visible: List<ActivityEntry>
        get() = filter.events?.let { set -> entries.filter { it.event in set } } ?: entries
}

@HiltViewModel
class ActivityLogViewModel @Inject constructor(private val audit: AuditLogger) : ViewModel() {
    private val _state = MutableStateFlow(ActivityUiState())
    val state: StateFlow<ActivityUiState> = _state.asStateFlow()

    private val line = Regex("^\\[(.+?)] (\\w+) \\| user=(.*?) \\| (.*?)(?: \\| h=[0-9a-f]{16})?$")

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val (entries, check) = withContext(Dispatchers.IO) {
                val parsed = audit.getRecentLogs(600).asReversed().mapNotNull { raw ->
                    line.find(raw)?.let { m ->
                        ActivityEntry(m.groupValues[1], m.groupValues[2], m.groupValues[3], m.groupValues[4])
                    }
                }
                parsed to audit.verify()
            }
            _state.update { it.copy(loading = false, entries = entries, check = check) }
        }
    }

    fun setFilter(f: ActivityFilter) = _state.update { it.copy(filter = f) }
}
