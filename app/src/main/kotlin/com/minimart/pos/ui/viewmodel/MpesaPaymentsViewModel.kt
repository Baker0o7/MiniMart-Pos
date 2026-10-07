package com.minimart.pos.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minimart.pos.data.entity.MpesaPayment
import com.minimart.pos.data.repository.MpesaPaymentRepository
import com.minimart.pos.data.repository.SettingsRepository
import com.minimart.pos.util.monthStartMs
import com.minimart.pos.util.todayStartMs
import com.minimart.pos.util.weekStartMs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

enum class MpesaPeriod(val label: String) { TODAY("Today"), WEEK("Week"), MONTH("Month"), YEAR("Year") }

data class MpesaTotals(val amount: Double = 0.0, val count: Int = 0)

/** Result of checking a transaction code against what this phone actually received from MPESA. */
sealed interface MpesaCheck {
    data class Found(val payment: MpesaPayment) : MpesaCheck
    data class NotFound(val code: String) : MpesaCheck
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MpesaPaymentsViewModel @Inject constructor(
    private val repo: MpesaPaymentRepository,
    private val settings: SettingsRepository
) : ViewModel() {

    private fun yearStartMs(): Long = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_YEAR, 1)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun startOf(p: MpesaPeriod) = when (p) {
        MpesaPeriod.TODAY -> todayStartMs()
        MpesaPeriod.WEEK -> weekStartMs()
        MpesaPeriod.MONTH -> monthStartMs()
        MpesaPeriod.YEAR -> yearStartMs()
    }

    val trackingEnabled: StateFlow<Boolean> = settings.mpesaSmsTracking
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(3000), false)
    val currency: StateFlow<String> = settings.currency
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(3000), "KES")

    private val _period = MutableStateFlow(MpesaPeriod.TODAY)
    val period: StateFlow<MpesaPeriod> = _period.asStateFlow()
    fun setPeriod(p: MpesaPeriod) { _period.value = p }

    val payments: StateFlow<List<MpesaPayment>> = _period
        .flatMapLatest { repo.getByRange(startOf(it), Long.MAX_VALUE) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(3000), emptyList())

    /** Totals for every period at once, so the summary cards never wait on the selected chip. */
    val totals: StateFlow<Map<MpesaPeriod, MpesaTotals>> = combine(
        MpesaPeriod.entries.map { p -> combine(repo.totalSince(startOf(p)), repo.countSince(startOf(p))) { a, c -> p to MpesaTotals(a, c) } }
    ) { it.toMap() }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(3000), emptyMap())

    private val _check = MutableStateFlow<MpesaCheck?>(null)
    val check: StateFlow<MpesaCheck?> = _check.asStateFlow()

    fun setTracking(on: Boolean) { viewModelScope.launch { settings.setMpesaSmsTracking(on) } }

    fun verify(code: String) {
        val clean = code.trim().uppercase()
        if (clean.length < 8) return
        viewModelScope.launch {
            _check.value = repo.findByCode(clean)?.let { MpesaCheck.Found(it) } ?: MpesaCheck.NotFound(clean)
        }
    }

    fun clearCheck() { _check.value = null }
}
