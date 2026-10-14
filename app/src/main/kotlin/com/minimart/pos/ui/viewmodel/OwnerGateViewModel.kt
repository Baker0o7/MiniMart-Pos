package com.minimart.pos.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.minimart.pos.util.PermissionGuard
import com.minimart.pos.util.RoleManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Lets a screen re-check, at the moment of a sensitive action, that the signed-in user really is the
 * owner. Hiding a button is not enough: a stale screen must not be able to restore a backup or
 * change payment credentials. A refusal is written to the audit log.
 */
@HiltViewModel
class OwnerGateViewModel @Inject constructor(private val guard: PermissionGuard) : ViewModel() {
    suspend fun allow(action: String): Boolean = guard.allow(action, RoleManager::canManageSystem)
}
