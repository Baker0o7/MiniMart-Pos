package com.minimart.pos.ui.viewmodel

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minimart.pos.data.dao.SyncDao
import com.minimart.pos.data.repository.ExpenseRepository
import com.minimart.pos.data.repository.MpesaPaymentRepository
import com.minimart.pos.data.repository.ProductRepository
import com.minimart.pos.data.repository.SaleRepository
import com.minimart.pos.data.repository.SettingsRepository
import com.minimart.pos.data.repository.ShiftRepository
import com.minimart.pos.util.BackupManager
import com.minimart.pos.util.BackupResult
import com.minimart.pos.util.EndOfDay
import com.minimart.pos.util.EndOfDayInputs
import com.minimart.pos.util.EndOfDayStep
import com.minimart.pos.util.ErrorAdvice
import com.minimart.pos.util.HeldCarts
import com.minimart.pos.util.todayStartMs
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EndOfDayState(
    val loading: Boolean = true,
    val steps: List<EndOfDayStep> = emptyList(),
    val canFinish: Boolean = false,
    val remaining: Int = 0,
    val working: Boolean = false,
    val message: String? = null,
    val messageIsError: Boolean = false
)

@HiltViewModel
class EndOfDayViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shifts: ShiftRepository,
    private val settings: SettingsRepository,
    private val syncDao: SyncDao,
    private val sales: SaleRepository,
    private val mpesa: MpesaPaymentRepository,
    private val expenses: ExpenseRepository,
    private val products: ProductRepository,
    private val prefs: SharedPreferences
) : ViewModel() {
    private val _state = MutableStateFlow(EndOfDayState())
    val state: StateFlow<EndOfDayState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            try {
                val start = todayStartMs()
                val open = shifts.openShifts().map { it.cashierName }
                val held = HeldCarts.decode(settings.heldCartsJson.first()).size
                val configured = !prefs.getString("peer_ip", "").isNullOrBlank() ||
                    runCatching { settings.syncServerEnabled.first() }.getOrDefault(false)
                val recon = runCatching { reconcileRange(mpesa, sales, start, Long.MAX_VALUE) }.getOrNull()
                val (lastOk, _, _) = BackupManager.healthSnapshot(context)
                val inputs = EndOfDayInputs(
                    openShifts = open,
                    heldCarts = held,
                    pendingSync = runCatching { syncDao.countPending() }.getOrDefault(0),
                    syncConfigured = configured,
                    unclaimedMpesaToday = recon?.unclaimedPayments?.size ?: 0,
                    backupDoneToday = lastOk >= start,
                    salesToday = sales.getSaleCountToday(start).first(),
                    expensesRecordedToday = (expenses.getTotalExpensesToday(start).first() ?: 0.0) > 0.0,
                    lowStock = runCatching { products.getLowStockProducts().first().size }.getOrDefault(0)
                )
                val steps = EndOfDay.steps(inputs)
                _state.update { it.copy(loading = false, steps = steps, canFinish = EndOfDay.canFinish(steps), remaining = EndOfDay.remaining(steps)) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, message = ErrorAdvice.explain(e, "Loading the end-of-day list"), messageIsError = true) }
            }
        }
    }

    fun backupNow() {
        if (_state.value.working) return
        _state.update { it.copy(working = true, message = null) }
        viewModelScope.launch {
            val result = try { BackupManager.backup(context) } catch (e: Exception) { BackupResult.Error(ErrorAdvice.explain(e, "The backup")) }
            _state.update {
                when (result) {
                    is BackupResult.Success -> it.copy(working = false, message = "Backup saved.", messageIsError = false)
                    is BackupResult.Error -> it.copy(working = false, message = result.message, messageIsError = true)
                }
            }
            refresh()
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }
}
