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
    val url: String = "",
    val anonKey: String = "",
    val setupCode: String = "",
    val connected: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
    val lastPushAt: Long = 0L
)

@HiltViewModel
class CloudViewModel @Inject constructor(
    private val config: CloudConfig,
    private val reporter: CloudReporter
) : ViewModel() {

    private val _state = MutableStateFlow(
        CloudUiState(url = config.url, anonKey = config.anonKey, connected = config.isConfigured, lastPushAt = config.lastPushAt)
    )
    val state: StateFlow<CloudUiState> = _state.asStateFlow()

    fun setUrl(v: String) = _state.update { it.copy(url = v) }
    fun setAnonKey(v: String) = _state.update { it.copy(anonKey = v) }
    fun setSetupCode(v: String) = _state.update { it.copy(setupCode = v) }

    fun connect() {
        val s = _state.value
        val url = s.url.trim().trimEnd('/')
        when {
            !url.startsWith("https://") -> return fail("Project URL must start with https://")
            s.anonKey.isBlank() -> return fail("Enter the project's anon (public) key")
            s.setupCode.isBlank() -> return fail("Enter the setup code you put in the SQL script")
        }
        config.url = url
        config.anonKey = s.anonKey
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

    private fun fail(msg: String) = _state.update { it.copy(message = msg, isError = true) }
}
