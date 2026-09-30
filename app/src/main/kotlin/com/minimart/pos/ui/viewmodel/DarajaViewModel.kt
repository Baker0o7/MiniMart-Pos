package com.minimart.pos.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minimart.pos.data.repository.SettingsRepository
import com.minimart.pos.mpesa.DarajaApiClient
import com.minimart.pos.mpesa.StkPushResult
import com.minimart.pos.mpesa.StkStatus
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
            _uiState.value = StkPushUiState(StkPushPhase.SENDING, "Sending payment request…")
            val normalizedPhone = client.normalizePhoneNumber(phone)
            when (val pushResult = client.initiateStkPush(normalizedPhone, amountKes, accountRef)) {
                is StkPushResult.Error -> {
                    _uiState.value = StkPushUiState(StkPushPhase.FAILED, pushResult.message)
                }
                is StkPushResult.Initiated -> {
                    _uiState.value = StkPushUiState(
                        StkPushPhase.AWAITING_CUSTOMER,
                        "Prompt sent — awaiting customer…",
                        secondsRemaining = 60
                    )
                    pollUntilResolved(pushResult.checkoutRequestId)
                }
            }
        }
    }

    private suspend fun pollUntilResolved(checkoutRequestId: String) {
        val timeoutMs = 60_000L
        val pollIntervalMs = 3_500L
        val startMs = System.currentTimeMillis()

        while (System.currentTimeMillis() - startMs < timeoutMs) {
            delay(pollIntervalMs)
            val elapsed  = System.currentTimeMillis() - startMs
            val remaining = ((timeoutMs - elapsed) / 1000).coerceAtLeast(0).toInt()
            _uiState.value = _uiState.value.copy(secondsRemaining = remaining)

            when (val status = client.queryStkStatus(checkoutRequestId)) {
                is StkStatus.Pending -> { /* keep polling */ }
                is StkStatus.Success -> {
                    _uiState.value = StkPushUiState(
                        StkPushPhase.SUCCESS,
                        "Payment confirmed ✓",
                        mpesaReceiptNumber = status.mpesaReceiptNumber
                    )
                    return
                }
                is StkStatus.Failed -> {
                    _uiState.value = StkPushUiState(StkPushPhase.FAILED, status.reason)
                    return
                }
                is StkStatus.Error -> {
                    // Transient network errors — keep polling unless timeout
                    _uiState.value = _uiState.value.copy(statusMessage = "Checking… (${status.message})")
                }
            }
        }

        // Timed out — cashier can enter ref manually from customer's SMS confirmation
        _uiState.value = StkPushUiState(
            StkPushPhase.TIMED_OUT,
            "Timed out — ask customer for M-Pesa SMS ref"
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
