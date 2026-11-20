package com.minimart.pos.ui.screen

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minimart.pos.ui.components.Text
import com.minimart.pos.ui.theme.DT
import com.minimart.pos.ui.viewmodel.CloudViewModel
import com.minimart.pos.util.Lang

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Owner account: "Continue with Google" (sign up and sign in are the same step), sign out, and account deletion. */
@Composable
internal fun CloudAccountSection(vm: CloudViewModel) {
    val cs by vm.state.collectAsStateWithLifecycle()
    val activity = LocalContext.current.findActivity()
    DSection(Lang.t("Owner account (Google)", "حساب المالك (Google)"), Icons.Default.AccountCircle,
        summary = if (cs.signedIn) cs.email.ifBlank { Lang.t("Signed in", "مسجّل الدخول") } else Lang.t("Not signed in", "غير مسجّل"),
        initiallyExpanded = false) {
        Text(Lang.t(
            "Sign in with your Google (Gmail) account to identify yourself as the owner. No shop data, sales or customers are sent anywhere. The till works fully without an account.",
            "سجّل الدخول بحساب Google (Gmail) لتعريف نفسك كمالك. لا تُرسل أي بيانات للمتجر أو المبيعات أو العملاء. تعمل الكاشيرة بالكامل بدون حساب."),
            color = DT.SubText, fontSize = 11.sp, lineHeight = 15.sp)
        Spacer(Modifier.height(10.dp))
        if (!cs.available) {
            Text(Lang.t("Google sign-in is not set up in this build yet (it needs the owner's Firebase project; see docs/FIREBASE.md).",
                "تسجيل الدخول عبر Google غير مفعّل في هذا الإصدار بعد (يحتاج إلى مشروع Firebase الخاص بالمالك؛ راجع docs/FIREBASE.md)."),
                color = DT.Amber, fontSize = 12.sp, lineHeight = 16.sp)
        } else if (!cs.signedIn) {
            Button(onClick = { activity?.let { vm.signIn(it) } }, enabled = !cs.busy && activity != null, shape = RoundedCornerShape(12.dp),
                modifier = Modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DT.Teal, disabledContainerColor = DT.Surface2)) {
                if (cs.busy) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                else Text(Lang.t("Continue with Google", "المتابعة عبر Google"), color = DT.Ink, fontSize = 13.sp)
            }
            Spacer(Modifier.height(4.dp))
            Text(Lang.t("New here? The same button creates your account the first time.", "جديد هنا؟ نفس الزر ينشئ حسابك في المرة الأولى."), color = DT.SubText, fontSize = 11.sp)
        } else {
            Text(Lang.t("Signed in as ${cs.email}", "مسجّل الدخول باسم ${cs.email}"), color = DT.Ink, fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = { vm.signOut() }, enabled = !cs.busy, shape = RoundedCornerShape(12.dp), modifier = Modifier.heightIn(min = 48.dp)) {
                Text(Lang.t("Sign out", "تسجيل الخروج"), color = DT.Red, fontSize = 13.sp)
            }
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = { vm.askDelete() }, enabled = !cs.busy, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(Lang.t("Delete my account", "احذف حسابي"), color = DT.Red, fontSize = 12.sp)
            }
        }
        cs.message?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = if (cs.isError) DT.Red else DT.Green, fontSize = 12.sp)
        }
    }
    if (cs.confirmDelete) {
        AlertDialog(onDismissRequest = { vm.cancelDelete() },
            title = { Text(Lang.t("Delete your account?", "حذف حسابك؟")) },
            text = { Text(Lang.t("This removes your Google sign-in account from MiniMart. Shop data on this phone is not touched. It cannot be undone.",
                "سيؤدي هذا إلى حذف حساب تسجيل الدخول عبر Google من MiniMart. بيانات المتجر على هذا الهاتف لن تتغير. لا يمكن التراجع.")) },
            confirmButton = { TextButton(onClick = { vm.deleteAccount() }) { Text(Lang.t("Delete", "حذف"), color = DT.Red) } },
            dismissButton = { TextButton(onClick = { vm.cancelDelete() }) { Text(Lang.t("Cancel", "إلغاء"), color = DT.SubText) } })
    }
}
