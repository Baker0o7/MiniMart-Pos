package com.minimart.pos.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minimart.pos.data.repository.ProductRepository
import com.minimart.pos.data.repository.SaleRepository
import com.minimart.pos.data.repository.SettingsRepository
import com.minimart.pos.data.repository.UserRepository
import com.minimart.pos.data.entity.User
import com.minimart.pos.data.entity.Product
import com.minimart.pos.data.dao.TopSellerResult
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
    val topSellers: List<TopSellerResult> = emptyList(),
    val cashierName: String = ""
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    saleRepo: SaleRepository,
    productRepo: ProductRepository,
    settingsRepo: SettingsRepository,
    userRepo: UserRepository
) : ViewModel() {

    private val todayStart: Long get() {
        val cal = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        return cal.timeInMillis
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        settingsRepo.storeName,
        settingsRepo.currency,
        saleRepo.getTotalRevenueToday(todayStart),
        saleRepo.getSaleCountToday(todayStart),
        productRepo.getLowStockProducts(),
        saleRepo.getTopSellers(todayStart),
        settingsRepo.loggedInUserId
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val storeName = values[0] as String
        val currency = values[1] as String
        val revenue = (values[2] as? Double) ?: 0.0
        val count = values[3] as Int
        val lowStock = values[4] as List<Product>
        val topSellers = values[5] as List<TopSellerResult>
        DashboardUiState(
            storeName = storeName,
            currency = currency,
            todayRevenue = revenue,
            todaySaleCount = count,
            lowStockProducts = lowStock,
            topSellers = topSellers
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())
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
            settingsRepo.loggedInUserId.collect { userId ->
                if (userId != null) {
                    val user = userRepo.getUserById(userId)
                    _uiState.update { it.copy(isLoggedIn = user != null, currentUser = user) }
                } else {
                    _uiState.update { it.copy(isLoggedIn = false, currentUser = null) }
                }
            }
        }
    }

    fun login(username: String, pin: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val user = userRepo.login(username, pin)
            if (user != null) {
                settingsRepo.setLoggedInUser(user.id)
                _uiState.update { it.copy(isLoading = false, isLoggedIn = true, currentUser = user) }
            } else {
                _uiState.update { it.copy(isLoading = false, error = "Invalid username or PIN") }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            settingsRepo.setLoggedInUser(null)
            _uiState.update { AuthUiState() }
        }
    }
}
