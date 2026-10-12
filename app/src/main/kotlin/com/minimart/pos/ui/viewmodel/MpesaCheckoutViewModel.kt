package com.minimart.pos.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minimart.pos.data.repository.MpesaAssessment
import com.minimart.pos.data.repository.MpesaPaymentRepository
import com.minimart.pos.data.repository.MpesaVerifier
import com.minimart.pos.data.repository.SaleRepository
import com.minimart.pos.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Live check of the M-Pesa code typed at checkout against payments this phone received. */
@HiltViewModel
class MpesaCheckoutViewModel @Inject constructor(
    private val payments: MpesaPaymentRepository,
    private val sales: SaleRepository,
    private val settings: SettingsRepository
) : ViewModel() {
    private val _assessment = MutableStateFlow(MpesaAssessment(com.minimart.pos.data.repository.MpesaVerdict.EMPTY))
    val assessment: StateFlow<MpesaAssessment> = _assessment.asStateFlow()

    val strict: StateFlow<Boolean> = settings.mpesaStrict
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(3000), false)

    private var job: Job? = null

    fun check(ref: String, due: Double) {
        job?.cancel()
        job = viewModelScope.launch {
            delay(250) // typing: wait for a pause
            val code = ref.trim()
            val received = if (code.isEmpty()) null else runCatching { payments.findByCode(code)?.amount }.getOrNull()
            val used = if (code.isEmpty()) 0 else runCatching { sales.mpesaRefUseCount(code) }.getOrDefault(0)
            val tracking = runCatching { settings.mpesaSmsTracking.first() }.getOrDefault(false)
            _assessment.value = MpesaVerifier.assess(code, due, received, used, tracking)
        }
    }
}
