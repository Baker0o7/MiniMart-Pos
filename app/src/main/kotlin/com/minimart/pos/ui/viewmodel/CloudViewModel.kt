package com.minimart.pos.ui.viewmodel

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minimart.pos.cloud.OwnerAuth
import com.minimart.pos.cloud.SignInResult
import com.minimart.pos.util.Lang
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
    val signedIn: Boolean = false,
    val email: String = "",
    val busy: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
    val confirmDelete: Boolean = false
)

/** The owner's Google (Gmail) account. Only sign-up/sign-in, sign-out and account deletion; no shop data leaves the phone. */
@HiltViewModel
class CloudViewModel @Inject constructor(private val auth: OwnerAuth) : ViewModel() {

    private val _state = MutableStateFlow(CloudUiState(available = auth.available, signedIn = auth.signedIn, email = auth.email))
    val state: StateFlow<CloudUiState> = _state.asStateFlow()

    /** "Continue with Google": creates the account the first time, signs in after that. */
    fun signIn(activity: Activity) {
        _state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            when (val r = auth.signInWithGoogle(activity)) {
                SignInResult.Cancelled -> _state.update { it.copy(busy = false) }
                is SignInResult.Failed -> _state.update { it.copy(busy = false, message = r.message, isError = true) }
                is SignInResult.Ok -> _state.update { it.copy(busy = false, signedIn = true, email = r.email, isError = false,
                    message = Lang.t("Signed in.", "تم تسجيل الدخول.")) }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            auth.signOut()
            _state.update { it.copy(signedIn = false, email = "", isError = false, message = Lang.t("Signed out.", "تم تسجيل الخروج.")) }
        }
    }

    fun askDelete() = _state.update { it.copy(confirmDelete = true) }
    fun cancelDelete() = _state.update { it.copy(confirmDelete = false) }

    /** Deletes the Google sign-in account from the app. Shop data on this phone is not touched. */
    fun deleteAccount() {
        _state.update { it.copy(busy = true, confirmDelete = false, message = null) }
        viewModelScope.launch {
            val problem = auth.deleteAccount()
            _state.update {
                if (problem != null) it.copy(busy = false, message = problem, isError = true)
                else it.copy(busy = false, signedIn = false, email = "", isError = false,
                    message = Lang.t("Your account was deleted. This phone's shop data is untouched.", "تم حذف حسابك. بيانات المتجر على هذا الهاتف لم تتغير."))
            }
        }
    }
}
