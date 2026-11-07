package com.minimart.pos.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minimart.pos.update.UpdateManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

/** Thin bridge so screens can show and drive the shared [UpdateManager] (the download outlives a screen). */
@HiltViewModel
class UpdateViewModel @Inject constructor(private val manager: UpdateManager) : ViewModel() {
    val state = manager.state
    var autoCheck: Boolean
        get() = manager.autoCheck
        set(v) { manager.autoCheck = v }
    fun check() { viewModelScope.launch { manager.check() } }
    fun install() {
        val s = manager.state.value
        val (r, a) = when (s) {
            is com.minimart.pos.update.UpdateState.Available -> s.release to s.asset
            is com.minimart.pos.update.UpdateState.NeedsPermission -> s.release to s.asset
            is com.minimart.pos.update.UpdateState.Failed -> (s.release ?: return) to (s.asset ?: return)
            else -> return
        }
        // Runs on the app-wide scope's manager, but is launched here; a rotation must not cancel a download.
        kotlinx.coroutines.GlobalScope.launch { manager.install(r, a) }
    }
    fun permissionIntent() = manager.permissionIntent()
    fun canInstall() = manager.canInstall()
}
