package com.minimart.pos.cloud

import android.app.Activity
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.GoogleAuthProvider
import com.minimart.pos.util.ErrorAdvice
import com.minimart.pos.util.Lang
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

sealed class SignInResult {
    data class Ok(val email: String) : SignInResult()
    /** The owner closed the Google sheet; nothing to report. */
    object Cancelled : SignInResult()
    data class Failed(val message: String) : SignInResult()
}

/**
 * The owner's account: "Continue with Google" through Android's Credential Manager, checked by Firebase Auth. The first
 * time a Google account signs in the account is created, so one button is both sign up and sign in. The till itself
 * never needs an account, and nothing is uploaded: the account only says who the owner is. Without app/google-services.json
 * (docs/FIREBASE.md) [available] is false and everything here stays idle.
 */
@Singleton
class OwnerAuth @Inject constructor(@ApplicationContext private val context: Context) {

    /** Set by the Google services plugin from google-services.json; empty when this build has no Firebase project. */
    private fun webClientId(): String {
        val id = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        return if (id == 0) "" else context.getString(id)
    }

    val available: Boolean get() = runCatching { FirebaseApp.getApps(context).isNotEmpty() && webClientId().isNotBlank() }.getOrDefault(false)

    private fun auth(): FirebaseAuth? = if (available) runCatching { FirebaseAuth.getInstance() }.getOrNull() else null

    val uid: String? get() = auth()?.currentUser?.uid
    val email: String get() = auth()?.currentUser?.email.orEmpty()
    val signedIn: Boolean get() = uid != null

    suspend fun signInWithGoogle(activity: Activity): SignInResult {
        val firebase = auth() ?: return SignInResult.Failed(
            Lang.t("This build is not linked to a Firebase project yet, so Google sign-in is unavailable.",
                "هذا الإصدار غير مرتبط بمشروع Firebase بعد، لذا تسجيل الدخول عبر Google غير متاح."))
        return try {
            val option = GetSignInWithGoogleOption.Builder(webClientId()).build()
            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
            val credential = CredentialManager.create(activity).getCredential(activity, request).credential
            if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)
                return SignInResult.Failed(Lang.t("That sign-in type is not supported.", "نوع تسجيل الدخول هذا غير مدعوم."))
            val google = GoogleIdTokenCredential.createFrom(credential.data)
            val result = firebase.signInWithCredential(GoogleAuthProvider.getCredential(google.idToken, null)).awaitResult()
            SignInResult.Ok(result.user?.email.orEmpty())
        } catch (_: GetCredentialCancellationException) {
            SignInResult.Cancelled
        } catch (_: NoCredentialException) {
            SignInResult.Failed(Lang.t("No Google account was found on this phone. Add one in the phone's Settings, then try again.",
                "لم يتم العثور على حساب Google على هذا الهاتف. أضف حسابًا من إعدادات الهاتف ثم أعد المحاولة."))
        } catch (e: GetCredentialException) {
            SignInResult.Failed(ErrorAdvice.explain(e, "Signing in with Google"))
        } catch (e: Exception) {
            SignInResult.Failed(ErrorAdvice.explain(e, "Signing in with Google"))
        }
    }

    suspend fun signOut() {
        auth()?.signOut()
        runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
    }

    /** Deletes the sign-in itself . Null means done. */
    suspend fun deleteAccount(): String? {
        val user = auth()?.currentUser ?: return null
        return try {
            user.delete().awaitResult()
            runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
            null
        } catch (_: FirebaseAuthRecentLoginRequiredException) {
            Lang.t("For safety, sign out, sign in with Google again, then delete the account straight away.",
                "للأمان، سجّل الخروج ثم سجّل الدخول عبر Google مرة أخرى، ثم احذف الحساب فورًا.")
        } catch (e: Exception) {
            ErrorAdvice.explain(e, "Deleting the account")
        }
    }
}
