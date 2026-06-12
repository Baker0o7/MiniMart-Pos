package com.minimart.pos.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minimart.pos.data.dao.SyncDao
import com.minimart.pos.sync.SyncClient
import com.minimart.pos.sync.SyncResult
import com.minimart.pos.sync.SyncServer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class SyncUiState(
    val serverRunning:  Boolean = false,
    val isSyncing:      Boolean = false,
    val lastResult:     String? = null,
    val pendingCount:   Int = 0,
    val serverIp:       String = "",
    val peerIp:         String = "",
    val deviceId:       String = ""
)

@HiltViewModel
class SyncViewModel @Inject constructor(
    private val server:  SyncServer,
    private val client:  SyncClient,
    private val syncDao: SyncDao,
    private val prefs:   android.content.SharedPreferences
) : ViewModel() {

    private val _state = MutableStateFlow(SyncUiState(
        deviceId = prefs.getString("device_id", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("device_id", it).apply()
        },
        peerIp   = prefs.getString("peer_ip", "") ?: ""
    ))
    val state: StateFlow<SyncUiState> = _state

    init {
        // Watch pending count
        viewModelScope.launch {
            syncDao.getPendingCount().collect { count ->
                _state.update { it.copy(pendingCount = count) }
            }
        }
        // Watch server state
        viewModelScope.launch {
            server.isRunning.collect { running ->
                _state.update { it.copy(
                    serverRunning = running,
                    serverIp = if (running) server.getLocalIp() else ""
                ) }
            }
        }
    }

    fun startServer() = viewModelScope.launch { server.start() }
    fun stopServer()  = viewModelScope.launch { server.stop() }

    fun setPeerIp(ip: String) {
        prefs.edit().putString("peer_ip", ip).apply()
        _state.update { it.copy(peerIp = ip) }
    }

    fun syncNow() = viewModelScope.launch {
        val ip = _state.value.peerIp
        if (ip.isBlank()) { _state.update { it.copy(lastResult = "Enter server IP first") }; return@launch }
        _state.update { it.copy(isSyncing = true, lastResult = null) }
        val result = client.sync(ip, _state.value.deviceId)
        _state.update { it.copy(
            isSyncing  = false,
            lastResult = when (result) {
                is SyncResult.Success -> "✓ Pushed ${result.pushed}, pulled ${result.pulled} changes"
                is SyncResult.Error   -> "✗ ${result.message}"
            }
        ) }
    }
}
