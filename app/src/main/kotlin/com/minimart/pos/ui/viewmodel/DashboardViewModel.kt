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
import com.minimart.pos.util.todayStartMs
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

// ─── Dashboard ────────────────────────────────────────────────────────────────

data class DashboardUiState(
    val storeName: String = "My MiniMart",
    val currency: String = "KES",
    val todayRevenue: Double = 0.0,
    val yesterdayRevenue: Double = 0.0,   // Bug fix: was hardcoded "+8%" in the UI
    val todaySaleCount: Int = 0,
    val lowStockProducts: List<Product> = emptyList(),
    val topSellers: List<TopSellerResult> = emptyList(),
    val expiringProducts: List<Product> = emptyList(),
    val expiredProducts: List<Product> = emptyList()
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

    // Bug fix: dailyFlowJobs MUST be declared before init{} because startDailyFlows()
    // (called in init) accesses it via .forEach { it.cancel() }. In Kotlin, member
    // properties initialize in declaration order — if this var appeared after init{},
    // it hadn't been set yet when startDailyFlows() ran, causing a NullPointerException
    // crash on app start every time. Moved above init{} to guarantee initialization order.
    private var dailyFlowJobs: List<kotlinx.coroutines.Job> = emptyList()

    init {
        viewModelScope.launch {
            settingsRepo.storeName.combine(settingsRepo.currency) { name, cur ->
                _uiState.update { it.copy(storeName = name, currency = cur) }
            }.catch { }.collect()
        }
        // Bug fix: the original code called getTotalRevenueToday(todayStart) once in
        // init{} — todayStart is computed correctly each time via get(), but passing
        // it once to a Room Flow bakes that timestamp forever. If the app stays alive
        // past midnight (auto-start on boot, left running overnight) the "today's"
        // query stays bound to the previous day's midnight, silently including all of
        // yesterday's sales in "today's revenue". The flows now live inside a function
        // that is re-invoked at midnight via a ticker, re-subscribing with a fresh date.
        startDailyFlows()
        viewModelScope.launch {
            // Tick once a minute and restart the daily flows whenever the calendar date changes.
            var lastDate = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR)
            while (true) {
                kotlinx.coroutines.delay(60_000)
                val today = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR)
                if (today != lastDate) { lastDate = today; startDailyFlows() }
            }
        }
        viewModelScope.launch {
            productRepo.getLowStockProducts()
                .catch { emit(emptyList()) }
                .collect { products ->
                    _uiState.update { it.copy(lowStockProducts = products) }
                }
        }
        // Expiry alerts — use combine() to avoid nested collect deadlock
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(
                settingsRepo.expiryAlertMonths.catch { emit(1) },
                productRepo.getAllProducts().catch { emit(emptyList()) }
            ) { months, all ->
                val now    = System.currentTimeMillis()
                val cutoff = now + months * 30L * 24 * 60 * 60 * 1000
                val expiring = all.filter { p -> p.expiryDate > 0L && p.expiryDate in now..cutoff }
                val expired  = all.filter { p -> p.expiryDate > 0L && p.expiryDate < now }
                Pair(expiring, expired)
            }.collect { (expiring, expired) ->
                _uiState.update { it.copy(expiringProducts = expiring, expiredProducts = expired) }
            }
        }
    }

    private fun startDailyFlows() {
        // Cancel previous subscriptions before re-subscribing with today's fresh timestamp
        dailyFlowJobs.forEach { it.cancel() }
        val ts = todayStart
        val yesterdayStart = ts - 24L * 60 * 60 * 1000
        dailyFlowJobs = listOf(
            viewModelScope.launch {
                saleRepo.getTotalRevenueToday(ts)
                    .catch { emit(null) }
                    .collect { _uiState.update { s -> s.copy(todayRevenue = it ?: 0.0) } }
            },
            viewModelScope.launch {
                // Yesterday's revenue (midnight yesterday to midnight today — not "last 24h")
                saleRepo.getTotalRevenueBetween(yesterdayStart, ts)
                    .catch { emit(null) }
                    .collect { rev ->
                        _uiState.update { s -> s.copy(yesterdayRevenue = rev ?: 0.0) }
                    }
            },
            viewModelScope.launch {
                saleRepo.getSaleCountToday(ts)
                    .catch { emit(0) }
                    .collect { _uiState.update { s -> s.copy(todaySaleCount = it) } }
            },
            viewModelScope.launch {
                saleRepo.getTopSellers(ts)
                    .catch { emit(emptyList()) }
                    .collect { _uiState.update { s -> s.copy(topSellers = it) } }
            }
        )
    }

    fun refresh() {
        viewModelScope.launch {
            val todayStart = todayStartMs()
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
    val error: String? = null,
    // Bug fix: lockout state now lives here (backed by persisted DataStore) instead of
    // LoginScreen's local `remember {}` state, which was wiped on process death —
    // force-closing the app trivially reset the "3 failed attempts" lockout.
    val isLockedOut: Boolean = false,
    val lockoutRemainingSeconds: Int = 0,
    val failedAttempts: Int = 0,
    // Bug fix: drives whether LoginScreen even shows the biometric prompt — only true
    // once a specific user has explicitly opted in via Settings (see SettingsRepository
    // .biometricUserId). Previously biometric login had no such gate at all.
    val biometricEnabled: Boolean = false
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val userRepo: UserRepository,
    private val settingsRepo: SettingsRepository,
    private val pinHasher: com.minimart.pos.util.PinHasher
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    companion object {
        private const val MAX_ATTEMPTS = 3
        private const val LOCKOUT_DURATION_MS = 30_000L
    }

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
        // Bug fix: drives whether the biometric prompt is even shown — only when a
        // specific user has explicitly opted in (see loginWithBiometric() below for the
        // matching fix on the login side).
        viewModelScope.launch {
            settingsRepo.biometricUserId.collect { id ->
                _uiState.update { it.copy(biometricEnabled = id != null && id != 0L) }
            }
        }
        // Bug fix: resume any in-progress lockout from the persisted deadline, so a
        // force-close mid-lockout doesn't reset it. Ticks once a second while locked.
        viewModelScope.launch {
            while (true) {
                val until = settingsRepo.lockoutUntilMs.first()
                val remainingMs = until - System.currentTimeMillis()
                if (remainingMs > 0) {
                    val attempts = settingsRepo.failedAttempts.first()
                    _uiState.update { it.copy(isLockedOut = true, lockoutRemainingSeconds = ((remainingMs + 999) / 1000).toInt(), failedAttempts = attempts) }
                } else if (_uiState.value.isLockedOut) {
                    // Lockout just expired — clear the persisted counters too
                    settingsRepo.clearLockout()
                    _uiState.update { it.copy(isLockedOut = false, lockoutRemainingSeconds = 0, failedAttempts = 0) }
                }
                kotlinx.coroutines.delay(1000)
            }
        }
    }

    fun login(username: String, pin: String) {
        viewModelScope.launch {
            // Bug fix: lockout used to be enforced only by disabling the UI client-side —
            // nothing stopped a direct vm.login() call (or a restarted app) from bypassing
            // it. Now re-checked against the persisted deadline before every attempt.
            val until = settingsRepo.lockoutUntilMs.first()
            if (until > System.currentTimeMillis()) {
                _uiState.update { it.copy(error = "Too many attempts. Please wait.") }
                return@launch
            }
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                // Use PinHasher.verify() which supports both Argon2id and legacy SHA-256
                val user = userRepo.loginWithHasher(username.trim(), pin.trim(), pinHasher)
                if (user != null) {
                    settingsRepo.setLoggedInUser(user.id)
                    settingsRepo.clearLockout()
                    // Silently upgrade legacy SHA-256 hash to Argon2id on successful login
                    if (pinHasher.needsUpgrade(user.pinHash)) {
                        try { userRepo.upgradePinHash(user.id, pinHasher.hash(pin.trim())) }
                        catch (_: Exception) {}
                    }
                    _uiState.update { it.copy(isLoading = false, isLoggedIn = true, currentUser = user, isLockedOut = false, failedAttempts = 0) }
                } else {
                    val attempts = settingsRepo.recordFailedAttempt(MAX_ATTEMPTS, LOCKOUT_DURATION_MS)
                    val nowLocked = attempts >= MAX_ATTEMPTS
                    _uiState.update {
                        it.copy(isLoading = false, error = "Invalid username or PIN",
                            isLockedOut = nowLocked, failedAttempts = attempts,
                            lockoutRemainingSeconds = if (nowLocked) (LOCKOUT_DURATION_MS / 1000).toInt() else 0)
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Login failed: ${e.message}") }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            try { settingsRepo.setLoggedInUser(null) } catch (_: Exception) {}
            _uiState.update { AuthUiState() }
        }
    }

    /** Called after biometric success — looks up the user by username only (no PIN check needed). */
    /** Bug fix: this used to take a `username: String` straight from whatever was typed
     * in LoginScreen's text field (default "admin") and log that account in on ANY
     * successful biometric match — with no verification the fingerprint/face belonged
     * to that account. Android's BiometricPrompt only confirms "a biometric enrolled on
     * this device matched," not "this specific app-user's biometric matched." On a
     * shared shop device with multiple enrolled fingerprints, that meant any of them
     * could become the Owner account instantly, bypassing PIN verification and lockout
     * entirely. Now resolves the one specific user ID that was explicitly bound via
     * Settings (which itself requires the account's PIN to set up) — there is no path
     * from "a fingerprint matched" to "log in as whatever username is typed" anymore. */
    fun loginWithBiometric() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val boundUserId = settingsRepo.biometricUserId.first()
                if (boundUserId == null) {
                    _uiState.update { it.copy(isLoading = false, error = "Biometric login not set up") }
                    return@launch
                }
                val user = userRepo.getUserById(boundUserId)
                if (user != null && user.isActive) {
                    settingsRepo.setLoggedInUser(user.id)
                    settingsRepo.clearLockout()
                    _uiState.update { it.copy(isLoading = false, isLoggedIn = true, currentUser = user) }
                } else {
                    _uiState.update { it.copy(isLoading = false, error = "Biometric account no longer available") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Biometric login failed") }
            }
        }
    }
}
