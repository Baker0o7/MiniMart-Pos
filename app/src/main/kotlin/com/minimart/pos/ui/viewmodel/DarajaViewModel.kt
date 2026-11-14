package com.minimart.pos.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minimart.pos.data.repository.SettingsRepository
import com.minimart.pos.mpesa.DarajaApiClient
import com.minimart.pos.mpesa.StkPushResult
import com.minimart.pos.mpesa.StkReference
import com.minimart.pos.mpesa.StkStatus
import com.minimart.pos.util.DarajaRules
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class StkPushPhase { IDLE, SENDING, AWAITING_CUSTOMER, SUCCESS, FAILED, TIMED_OUT }

data class StkPushUiState(
    val phase: StkPushPhase = StkPushPhase.IDLE,
    val statusMessage: String = "",
    val mpesaReceiptNumber: String? = null,
    val secondsRemaining: Int = 0
)

@HiltViewModel
class DarajaViewModel @Inject constructor(
    private val client: DarajaApiClient,
    private val settingsRepo: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StkPushUiState())
    val uiState: StateFlow<StkPushUiState> = _uiState

    private var pollJob: Job? = null

    val isConfigured: Boolean get() = settingsRepo.getDarajaConfig().isConfigured

    fun sendPushAndAwaitResult(phone: String, amountKes: Int, accountRef: String = "MiniMart") {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            _uiState.value = StkPushUiState(StkPushPhase.SENDING, DarajaRules.sending())
            val normalizedPhone = client.normalizePhoneNumber(phone)
            when (val pushResult = client.initiateStkPush(normalizedPhone, amountKes, accountRef)) {
                is StkPushResult.Error -> {
                    _uiState.value = StkPushUiState(StkPushPhase.FAILED, pushResult.message)
                }
                is StkPushResult.Initiated -> {
                    _uiState.value = StkPushUiState(
                        StkPushPhase.AWAITING_CUSTOMER,
                        DarajaRules.awaiting(),
                        secondsRemaining = 60
                    )
                    pollUntilResolved(pushResult.checkoutRequestId)
                }
            }
        }
    }

    private suspend fun pollUntilResolved(checkoutRequestId: String) {
        val timeoutMs = 60_000L
        val startMs = System.currentTimeMillis()

        var errorsInARow = 0
        while (System.currentTimeMillis() - startMs < timeoutMs) {
            delay(DarajaRules.pollDelayMs(errorsInARow))
            val elapsed  = System.currentTimeMillis() - startMs
            val remaining = ((timeoutMs - elapsed) / 1000).coerceAtLeast(0).toInt()
            _uiState.value = _uiState.value.copy(secondsRemaining = remaining)

            when (val status = client.queryStkStatus(checkoutRequestId)) {
                is StkStatus.Pending -> { errorsInARow = 0 }
                is StkStatus.Success -> {
                    _uiState.value = StkPushUiState(
                        StkPushPhase.SUCCESS,
                        DarajaRules.confirmed(),
                        mpesaReceiptNumber = StkReference.forPayment(status.mpesaReceiptNumber, checkoutRequestId)
                    )
                    return
                }
                is StkStatus.Failed -> {
                    _uiState.value = StkPushUiState(StkPushPhase.FAILED, status.reason)
                    return
                }
                is StkStatus.Error -> {
                    // Transient network errors — keep polling (more slowly each time) unless timeout
                    errorsInARow++
                    _uiState.value = _uiState.value.copy(statusMessage = DarajaRules.checkingAgain(status.message))
                }
            }
        }

        // Timed out — cashier can enter ref manually from customer's SMS confirmation
        _uiState.value = StkPushUiState(
            StkPushPhase.TIMED_OUT,
            DarajaRules.timedOut()
        )
    }

    fun cancel() {
        pollJob?.cancel()
        pollJob = null
        _uiState.value = StkPushUiState(StkPushPhase.IDLE)
    }

    fun reset() {
        cancel()
    }
}
