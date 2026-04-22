package com.minimart.pos.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minimart.pos.data.dao.TopSellerResult
import com.minimart.pos.data.entity.Product
import com.minimart.pos.data.entity.User
import com.minimart.pos.data.repository.ProductRepository
import com.minimart.pos.data.repository.SaleRepository
import com.minimart.pos.data.repository.SettingsRepository
import com.minimart.pos.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

// ─── Dashboard ────────────────────────────────────────────────────────────────

data class DashboardUiState(
    val storeName: String = "My MiniMart",
    val currency: String = "KES",
    val todayRevenue: Double = 0.0,
    val todaySaleCount: Int = 0,
    val lowStockProducts: List<Product> = emptyList(),
    val topSellers: List<TopSellerResult> = emptyList()
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val saleRepo: SaleRepository,
    private val productRepo: ProductRepository,
    private val settingsRepo: SettingsRepository,
    userRepo: UserRepository
) : ViewModel() {

    private val todayStart: Long
        get() {
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            return cal.timeInMillis
        }

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepo.storeName.combine(settingsRepo.currency) { name, cur ->
                _uiState.update { it.copy(storeName = name, currency = cur) }
            }.catch { }.collect()
        }
        viewModelScope.launch {
            saleRepo.getTotalRevenueToday(todayStart)
                .catch { emit(null) }
                .collect { revenue ->
                    _uiState.update { it.copy(todayRevenue = revenue ?: 0.0) }
                }
        }
        viewModelScope.launch {
            saleRepo.getSaleCountToday(todayStart)
                .catch { emit(0) }
                .collect { count ->
                    _uiState.update { it.copy(todaySaleCount = count) }
                }
        }
        viewModelScope.launch {
            productRepo.getLowStockProducts()
                .catch { emit(emptyList()) }
                .collect { products ->
                    _uiState.update { it.copy(lowStockProducts = products) }
                }
        }
        viewModelScope.launch {
            saleRepo.getTopSellers(todayStart)
                .catch { emit(emptyList()) }
                .collect { sellers ->
                    _uiState.update { it.copy(topSellers = sellers) }
                }
        }
    }

    /** Pull-to-refresh: re-trigger a fresh load of today's stats */
    fun refresh() {
        viewModelScope.launch {
            val todayStart = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis
            try {
                val revenue = saleRepo.getTotalRevenueToday(todayStart).first() ?: 0.0
                val count = saleRepo.getSaleCountToday(todayStart).first()
                val low = productRepo.getLowStockProducts().first()
                val top = saleRepo.getTopSellers(todayStart).first()
                _uiState.update { it.copy(todayRevenue = revenue, todaySaleCount = count, lowStockProducts = low, topSellers = top) }
            } catch (_: Exception) {}
        }
    }
}

// ─── Auth ─────────────────────────────────────────────────────────────────────

data class AuthUiState(
    val isLoading: Boolean = false,
    val isLoggedIn: Boolean = false,
    val currentUser: User? = null,
    val error: String? = null
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val userRepo: UserRepository,
    private val settingsRepo: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepo.loggedInUserId
                .catch { emit(null) }
                .collect { userId ->
                    if (userId != null) {
                        try {
                            val user = userRepo.getUserById(userId)
                            _uiState.update { it.copy(isLoggedIn = user != null, currentUser = user) }
                        } catch (e: Exception) {
                            _uiState.update { it.copy(isLoggedIn = false, currentUser = null) }
                        }
                    } else {
                        _uiState.update { it.copy(isLoggedIn = false, currentUser = null) }
                    }
                }
        }
    }

    fun login(username: String, pin: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val user = userRepo.login(username.trim(), pin.trim())
                if (user != null) {
                    settingsRepo.setLoggedInUser(user.id)
                    _uiState.update { it.copy(isLoading = false, isLoggedIn = true, currentUser = user) }
                } else {
                    _uiState.update { it.copy(isLoading = false, error = "Invalid username or PIN") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Login failed: ${e.message}") }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            try {
                settingsRepo.setLoggedInUser(null)
            } catch (_: Exception) {}
            _uiState.update { AuthUiState() }
        }
    }

    /** Called after biometric success — looks up the user by username only (no PIN check needed). */
    fun loginWithBiometric(username: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val user = userRepo.getUserByUsername(username.trim())
                if (user != null && user.isActive) {
                    settingsRepo.setLoggedInUser(user.id)
                    _uiState.update { it.copy(isLoading = false, isLoggedIn = true, currentUser = user) }
                } else {
                    _uiState.update { it.copy(isLoading = false, error = "User not found") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Biometric login failed") }
            }
        }
    }
}
