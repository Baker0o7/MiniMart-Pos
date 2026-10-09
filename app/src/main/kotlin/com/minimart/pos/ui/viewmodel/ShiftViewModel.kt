package com.minimart.pos.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minimart.pos.data.entity.Shift
import com.minimart.pos.data.repository.SettingsRepository
import com.minimart.pos.data.repository.ShiftRepository
import com.minimart.pos.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ShiftUiState(
    val activeShift: Shift? = null,
    val allShifts: List<Shift> = emptyList(),
    val isLoading: Boolean = false,
    val successMessage: String? = null,
    val error: String? = null,
    val lastClosedShift: Shift? = null,
    // Bug fix: ShiftScreen hardcoded "KES" in 8 places despite the app having a
    // configurable currency setting — SettingsRepository was already injected here
    // but its currency Flow was never exposed to the UI state.
    val currency: String = "KES",
    // Blind close: cashiers don't see expected cash or the over/short figure; managers/owners do.
    val blindClose: Boolean = true,
    val canSeeVariance: Boolean = false
)

@HiltViewModel
class ShiftViewModel @Inject constructor(
    private val shiftRepo: ShiftRepository,
    private val userRepo: UserRepository,
    private val settingsRepo: SettingsRepository,
    private val printer: com.minimart.pos.printer.ThermalPrinter,
    private val cashDrawer: com.minimart.pos.printer.CashDrawerManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShiftUiState())
    val uiState: StateFlow<ShiftUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepo.currency.catch { emit("KES") }.collect { cur ->
                _uiState.update { it.copy(currency = cur) }
            }
        }
        viewModelScope.launch {
            combine(settingsRepo.blindClose, settingsRepo.loggedInUserId) { blind, uid -> blind to uid }
                .catch { }
                .collect { (blind, uid) ->
                    val role = uid?.let { userRepo.getUserById(it)?.role }
                    _uiState.update {
                        it.copy(blindClose = blind, canSeeVariance = !blind || com.minimart.pos.util.RoleManager.canViewReports(role))
                    }
                }
        }
        viewModelScope.launch {
            // Load all recent shifts
            shiftRepo.getRecentShifts(System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000)
                .catch { emit(emptyList()) }
                .collect { shifts ->
                    _uiState.update { it.copy(allShifts = shifts) }
                }
        }
        viewModelScope.launch {
            settingsRepo.loggedInUserId.catch { emit(null) }.collect { userId ->
                if (userId != null) {
                    val open = shiftRepo.getOpenShift(userId)
                    _uiState.update { it.copy(activeShift = open) }
                } else {
                    _uiState.update { it.copy(activeShift = null) }
                }
            }
        }
    }

    fun clockIn(openingFloat: Double) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val userId = settingsRepo.loggedInUserId.first()
                val user = userId?.let { userRepo.getUserById(it) }
                if (userId == null || user == null) {
                    _uiState.update { it.copy(isLoading = false, error = "Clock-in failed: no signed-in user") }
                    return@launch
                }
                val shiftId = shiftRepo.clockIn(userId, user.displayName, openingFloat)
                val shift = shiftRepo.getOpenShift(userId)
                _uiState.update { it.copy(isLoading = false, activeShift = shift, successMessage = "Shift started! Good luck ${user.displayName} 👋") }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Clock-in failed: ${e.message}") }
            }
        }
    }

    fun clockOut(closingFloat: Double, notes: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val shift = _uiState.value.activeShift
                if (shift == null) {
                    _uiState.update { it.copy(isLoading = false, error = "No open shift to close") }
                    return@launch
                }
                val closed = shiftRepo.clockOut(shift.id, closingFloat, notes)
                _uiState.update { it.copy(isLoading = false, activeShift = null, lastClosedShift = closed, successMessage = "Shift ended") }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Clock-out failed: ${e.message}") }
            }
        }
    }

    /** Z-report text for [shiftId], honouring blind close for the current viewer. */
    suspend fun zText(shiftId: Long): String? {
        val z = shiftRepo.zReport(shiftId) ?: return null
        val store = try { settingsRepo.storeName.first() } catch (_: Exception) { "" }
        val st = _uiState.value
        return com.minimart.pos.util.ZReportFormatter.format(z, store, st.currency, st.canSeeVariance)
    }

    fun printZReport(shiftId: Long) {
        viewModelScope.launch {
            val text = try { zText(shiftId) } catch (e: Exception) { null }
            if (text == null) { _uiState.update { it.copy(error = "Report not found") }; return@launch }
            when (val r = printer.printPlainText(text)) {
                is com.minimart.pos.printer.PrintResult.Success -> _uiState.update { it.copy(successMessage = "Z-report printed") }
                is com.minimart.pos.printer.PrintResult.Error -> _uiState.update { it.copy(error = r.message + " — use Share instead") }
            }
        }
    }

    /** Opens the drawer. A no-sale open during a shift is counted and shown on the Z-report. */
    fun openDrawer(noSale: Boolean) {
        viewModelScope.launch {
            val r = cashDrawer.openDrawer()
            if (r is com.minimart.pos.printer.DrawerResult.Success) {
                val shift = _uiState.value.activeShift
                if (noSale && shift != null) runCatching { settingsRepo.recordNoSaleOpen(shift.id) }
                _uiState.update { it.copy(successMessage = "Drawer opened") }
            } else if (r is com.minimart.pos.printer.DrawerResult.Error) {
                _uiState.update { it.copy(error = "Drawer: ${r.msg}") }
            }
        }
    }

    fun clearMessages() { _uiState.update { it.copy(successMessage = null, error = null) } }
}
