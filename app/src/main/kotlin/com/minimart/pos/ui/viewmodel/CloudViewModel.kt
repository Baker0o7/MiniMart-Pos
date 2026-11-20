package com.minimart.pos.ui.viewmodel

import com.minimart.pos.util.Lang
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.app.Activity
import com.minimart.pos.cloud.CloudConfig
import com.minimart.pos.cloud.OwnerAuth
import com.minimart.pos.cloud.SignInResult
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
    /** This build has a Firebase project, so Google sign-in can work. */
    val available: Boolean = false,
    val connected: Boolean = false,
    val email: String = "",
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
    val takeOverPrompt: Boolean = false,
    val confirmDelete: Boolean = false
)

@HiltViewModel
class CloudViewModel @Inject constructor(
    private val config: CloudConfig,
    private val auth: OwnerAuth,
    private val reporter: CloudReporter,
    private val catalog: com.minimart.pos.cloud.CatalogSync
) : ViewModel() {

    private val _state = MutableStateFlow(
        CloudUiState(available = auth.available, connected = config.isConfigured, email = auth.email, lastPushAt = config.lastPushAt,
            catalogRole = config.catalogRole, catalogAt = config.catalogAt)
    )
    val state: StateFlow<CloudUiState> = _state.asStateFlow()

    /** "Continue with Google": creates the account the first time, signs in after that. */
    fun signIn(activity: Activity) {
        _state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            when (val r = auth.signInWithGoogle(activity)) {
                SignInResult.Cancelled -> _state.update { it.copy(busy = false) }
                is SignInResult.Failed -> _state.update { it.copy(busy = false, message = r.message, isError = true) }
                is SignInResult.Ok -> {
                    val p = reporter.push()
                    _state.update {
                        it.copy(busy = false, connected = true, email = r.email, lastPushAt = config.lastPushAt,
                            message = if (p is CloudResult.Failed) Lang.t("Signed in, but the first upload failed: ${p.message}", "تم تسجيل الدخول لكن فشل الرفع الأول: ${p.message}") else Lang.t("Signed in. Store uploaded.", "تم تسجيل الدخول ورُفع المتجر."),
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
                CloudResult.Ok -> _state.update { it.copy(busy = false, message = Lang.t("Uploaded.", "تم الرفع."), isError = false, lastPushAt = config.lastPushAt) }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            auth.signOut()
            config.disconnect()
            _state.update { it.copy(connected = false, email = "", catalogRole = 0, message = Lang.t("Signed out. Your cloud copy is kept; sign in again to continue.", "تم تسجيل الخروج. نسختك السحابية محفوظة؛ سجّل الدخول للمتابعة."), isError = false, lastPushAt = 0L) }
        }
    }

    fun askDelete() = _state.update { it.copy(confirmDelete = true) }
    fun cancelDelete() = _state.update { it.copy(confirmDelete = false) }

    /** Removes the cloud copy, then the account itself. Shop data on this phone is not touched. */
    fun deleteAccount() {
        _state.update { it.copy(busy = true, confirmDelete = false, message = null) }
        viewModelScope.launch {
            when (val r = reporter.deleteCloudData()) {
                is CloudResult.Failed -> return@launch _state.update { it.copy(busy = false, message = r.message, isError = true) }
                CloudResult.Ok -> Unit
            }
            val problem = auth.deleteAccount()
            if (problem != null) return@launch _state.update { it.copy(busy = false, message = problem, isError = true) }
            config.disconnect()
            _state.update { it.copy(busy = false, connected = false, email = "", catalogRole = 0, lastPushAt = 0L, isError = false,
                message = Lang.t("Your account and cloud copy were deleted. This phone's shop data is untouched.", "تم حذف حسابك ونسختك السحابية. بيانات المتجر على هذا الهاتف لم تتغير.")) }
        }
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
}
