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
    val lastClosedShift: Shift? = null
)

@HiltViewModel
class ShiftViewModel @Inject constructor(
    private val shiftRepo: ShiftRepository,
    private val userRepo: UserRepository,
    private val settingsRepo: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShiftUiState())
    val uiState: StateFlow<ShiftUiState> = _uiState.asStateFlow()

    init {
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
                }
            }
        }
    }

    fun clockIn(openingFloat: Double) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val userId = settingsRepo.loggedInUserId.first() ?: return@launch
                val user = userRepo.getUserById(userId) ?: return@launch
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
                val shift = _uiState.value.activeShift ?: return@launch
                val closed = shiftRepo.clockOut(shift.id, closingFloat, notes)
                _uiState.update { it.copy(isLoading = false, activeShift = null, lastClosedShift = closed, successMessage = "Shift ended") }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Clock-out failed: ${e.message}") }
            }
        }
    }

    fun clearMessages() { _uiState.update { it.copy(successMessage = null, error = null) } }
}
