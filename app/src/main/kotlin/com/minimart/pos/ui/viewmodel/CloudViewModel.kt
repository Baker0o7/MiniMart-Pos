package com.minimart.pos.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minimart.pos.cloud.CloudConfig
import com.minimart.pos.cloud.CloudReporter
import com.minimart.pos.cloud.CloudResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CloudUiState(
    /** The operator's hosted backend is used: owners only type a code from the web dashboard. */
    val hosted: Boolean = false,
    val url: String = "",
    val anonKey: String = "",
    val setupCode: String = "",
    val connected: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
    val lastPushAt: Long = 0L,
    /** Shared product list: 0 off, 1 main shop (publishes), 2 follows the main shop. */
    val catalogRole: Int = 0,
    val catalogBusy: Boolean = false,
    val catalogMessage: String? = null,
    val catalogError: Boolean = false,
    val catalogAt: Long = 0L,
    val takeOverPrompt: Boolean = false
)

@HiltViewModel
class CloudViewModel @Inject constructor(
    private val config: CloudConfig,
    private val reporter: CloudReporter,
    private val catalog: com.minimart.pos.cloud.CatalogSync
) : ViewModel() {

    private val _state = MutableStateFlow(
        CloudUiState(hosted = config.usesHosted, url = config.url, anonKey = config.anonKey, connected = config.isConfigured, lastPushAt = config.lastPushAt,
            catalogRole = config.catalogRole, catalogAt = config.catalogAt)
    )
    val state: StateFlow<CloudUiState> = _state.asStateFlow()

    fun setUrl(v: String) = _state.update { it.copy(url = v) }
    fun setAnonKey(v: String) = _state.update { it.copy(anonKey = v) }
    fun setSetupCode(v: String) = _state.update { it.copy(setupCode = v) }

    fun connect() {
        val s = _state.value
        val url = s.url.trim().trimEnd('/')
        when {
            !config.usesHosted && !url.startsWith("https://") -> return fail("Project URL must start with https://")
            !config.usesHosted && s.anonKey.isBlank() -> return fail("Enter the project's anon (public) key")
            s.setupCode.isBlank() -> return fail(if (config.usesHosted) "Enter the code from your dashboard (Add a shop)" else "Enter the setup code you put in the SQL script")
        }
        if (!config.usesHosted) { config.url = url; config.anonKey = s.anonKey }
        _state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            when (val r = reporter.register(s.setupCode.trim())) {
                is CloudResult.Failed -> _state.update { it.copy(busy = false, message = r.message, isError = true) }
                CloudResult.Ok -> {
                    config.registered = true
                    val p = reporter.push()
                    _state.update {
                        it.copy(busy = false, connected = true, setupCode = "", lastPushAt = config.lastPushAt,
                            message = if (p is CloudResult.Failed) "Connected, but first upload failed: ${p.message}" else "Connected. Store uploaded.",
                            isError = p is CloudResult.Failed)
                    }
                }
            }
        }
    }

    fun uploadNow() {
        _state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            when (val r = reporter.push()) {
                is CloudResult.Failed -> _state.update { it.copy(busy = false, message = r.message, isError = true) }
                CloudResult.Ok -> _state.update { it.copy(busy = false, message = "Uploaded.", isError = false, lastPushAt = config.lastPushAt) }
            }
        }
    }

    fun disconnect() {
        config.disconnect()
        _state.update { it.copy(connected = false, message = "Disconnected. Reconnecting registers this phone as a new store.", isError = false, lastPushAt = 0L) }
    }

    fun setCatalogRole(role: Int) {
        config.catalogRole = role
        _state.update { it.copy(catalogRole = role, catalogMessage = null, takeOverPrompt = false) }
    }

    /** Publishes (main shop) or fetches (following shop) right now. */
    fun catalogNow(takeOver: Boolean = false) {
        val role = config.catalogRole
        if (role == 0) return
        _state.update { it.copy(catalogBusy = true, catalogMessage = null, takeOverPrompt = false) }
        viewModelScope.launch {
            val r = if (role == 1) catalog.publish(takeOver = takeOver, force = true) else catalog.pull()
            _state.update {
                when (r) {
                    is com.minimart.pos.cloud.CatalogOutcome.Done -> it.copy(catalogBusy = false, catalogMessage = r.detail, catalogError = false, catalogAt = config.catalogAt)
                    is com.minimart.pos.cloud.CatalogOutcome.Failed -> it.copy(catalogBusy = false, catalogMessage = r.message, catalogError = true, takeOverPrompt = r.anotherPublisher)
                }
            }
        }
    }

    fun dismissTakeOver() = _state.update { it.copy(takeOverPrompt = false) }

    private fun fail(msg: String) = _state.update { it.copy(message = msg, isError = true) }
}
