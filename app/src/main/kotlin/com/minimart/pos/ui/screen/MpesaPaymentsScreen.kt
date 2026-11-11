package com.minimart.pos.ui.screen

import com.minimart.pos.ui.components.Text
import com.minimart.pos.util.ArabicCount
import com.minimart.pos.util.Lang
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.minimart.pos.data.entity.MpesaPayment
import com.minimart.pos.ui.theme.DT
import com.minimart.pos.ui.viewmodel.MpesaCheck
import com.minimart.pos.ui.viewmodel.MpesaPaymentsViewModel
import com.minimart.pos.ui.viewmodel.MpesaPeriod
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun mpesaMoney(currency: String, v: Double) = "$currency ${String.format(Locale.US, "%,.2f", v)}"

/**
 * Incoming M-Pesa payments read from SMS. Managers see totals and the full list and can switch
 * tracking on; every role gets the "Verify a code" check, which only trusts messages this phone
 * actually received from the MPESA sender ID.
 */
@Composable
fun MpesaPaymentsScreen(
    onBack: () -> Unit,
    canManage: Boolean,
    vm: MpesaPaymentsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val enabled by vm.trackingEnabled.collectAsStateWithLifecycle()
    val currency by vm.currency.collectAsStateWithLifecycle()
    val period by vm.period.collectAsStateWithLifecycle()
    val payments by vm.payments.collectAsStateWithLifecycle()
    val claimed by vm.claimed.collectAsStateWithLifecycle()
    val strict by vm.strict.collectAsStateWithLifecycle()
    val totals by vm.totals.collectAsStateWithLifecycle()
    val check by vm.check.collectAsStateWithLifecycle()
    val recon by vm.reconciliation.collectAsStateWithLifecycle()

    fun hasPermission() = ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
    var permitted by remember { mutableStateOf(hasPermission()) }
    var denied by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permitted = granted
        denied = !granted
        vm.setTracking(granted)
    }

    Box(Modifier.fillMaxSize().background(DT.Bg)) {
        Column(Modifier.fillMaxSize()) {
            GradientHeader(
                title = tr(com.minimart.pos.R.string.mpesa_payments),
                subtitle = if (enabled && permitted) Lang.t("Tracking incoming payments", "تتبع المدفوعات الواردة") else Lang.t("Tracking is off", "التتبع متوقف"),
                onBack = onBack
            )
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ── Verify a code (everyone) ───────────────────────────────────
                item { VerifyCard(check, currency, onVerify = vm::verify, onClear = vm::clearCheck) }

                if (canManage) {
                    // ── Tracking switch ────────────────────────────────────────
                    item {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(DT.Surface)
                                .border(1.dp, DT.Border, RoundedCornerShape(16.dp)).padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(tr(com.minimart.pos.R.string.log_auto), color = DT.OnSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(
                                    when {
                                        denied -> Lang.t("SMS permission was refused. Allow it in phone Settings › Apps › MiniMart POS.", "تم رفض إذن الرسائل. اسمح به من إعدادات الهاتف ‹ التطبيقات ‹ MiniMart POS.")
                                        enabled && !permitted -> Lang.t("Needs SMS permission to read M-Pesa confirmations.", "يحتاج إلى إذن الرسائل لقراءة تأكيدات إم-بيسا.")
                                        else -> Lang.t("Reads M-Pesa confirmation texts on this phone. Texts from ordinary numbers are ignored.", "يقرأ رسائل تأكيد إم-بيسا على هذا الهاتف. تُتجاهل الرسائل الواردة من الأرقام العادية.")
                                    },
                                    color = if (denied || (enabled && !permitted)) DT.Amber else DT.SubText, fontSize = 12.sp
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Switch(
                                checked = enabled && permitted,
                                onCheckedChange = { on ->
                                    if (!on) vm.setTracking(false)
                                    else if (hasPermission()) { permitted = true; vm.setTracking(true) }
                                    else launcher.launch(Manifest.permission.RECEIVE_SMS)
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = DT.Teal, uncheckedTrackColor = DT.Border, uncheckedThumbColor = DT.SubText, uncheckedBorderColor = DT.SubText.copy(0.6f))
                            )
                        }
                    }

                    item {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(DT.Surface)
                                .border(1.dp, DT.Border, RoundedCornerShape(16.dp)).padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(tr(com.minimart.pos.R.string.require_verified), color = DT.OnSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(tr(com.minimart.pos.R.string.mpesa_strict_help),
                                    color = DT.SubText, fontSize = 12.sp)
                            }
                            Spacer(Modifier.width(10.dp))
                            Switch(checked = strict, onCheckedChange = { vm.setStrict(it) },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = DT.Teal, uncheckedTrackColor = DT.Border, uncheckedThumbColor = DT.SubText, uncheckedBorderColor = DT.SubText.copy(0.6f)))
                        }
                    }

                    // ── Totals ─────────────────────────────────────────────────
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                TotalCard(MpesaPeriod.TODAY, totals[MpesaPeriod.TODAY], currency, Modifier.weight(1f))
                                TotalCard(MpesaPeriod.WEEK, totals[MpesaPeriod.WEEK], currency, Modifier.weight(1f))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                TotalCard(MpesaPeriod.MONTH, totals[MpesaPeriod.MONTH], currency, Modifier.weight(1f))
                                TotalCard(MpesaPeriod.YEAR, totals[MpesaPeriod.YEAR], currency, Modifier.weight(1f))
                            }
                        }
                    }

                    // ── List ───────────────────────────────────────────────────
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MpesaPeriod.entries.forEach { p ->
                                val sel = p == period
                                Box(
                                    Modifier.height(36.dp).clip(RoundedCornerShape(18.dp))
                                        .background(if (sel) DT.Teal else DT.Surface)
                                        .border(1.dp, if (sel) DT.Teal else DT.Border, RoundedCornerShape(18.dp))
                                        .clickable(role = androidx.compose.ui.semantics.Role.Button, indication = null, interactionSource = remember { MutableInteractionSource() }) { vm.setPeriod(p) }
                                        .padding(horizontal = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) { Text(p.label, color = if (sel) Color.White else DT.SubText, fontSize = 13.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium) }
                            }
                        }
                    }
                    item { ReconciliationCard(recon, currency, period.label) }
                    if (payments.isEmpty()) {
                        item {
                            Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.PhoneAndroid, null, tint = DT.SubText.copy(0.4f), modifier = Modifier.size(52.dp))
                                Spacer(Modifier.height(8.dp))
                                Text(if (enabled && permitted) Lang.t("No M-Pesa payments yet for ${period.label.lowercase()}", "لا توجد مدفوعات إم-بيسا بعد (${period.label})") else Lang.t("Turn on tracking to start logging payments", "شغّل التتبع لبدء تسجيل المدفوعات"),
                                    color = DT.SubText, fontSize = 13.sp)
                            }
                        }
                    } else {
                        items(payments, key = { it.id }) { PaymentRow(it, currency, it.code.uppercase() in claimed) }
                    }
                }
            }
        }
    }
}

/** Does the money that arrived match the sales that say they were paid by M-Pesa? */
@Composable
private fun ReconciliationCard(recon: com.minimart.pos.util.MpesaReconciliation?, currency: String, periodLabel: String) {
    if (recon == null) return
    val ok = !recon.needsAttention
    val tone = if (ok) DT.Green else DT.Amber
    var open by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(DT.Surface)
            .border(1.dp, tone.copy(0.6f), RoundedCornerShape(18.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (ok) Icons.Default.CheckCircle else Icons.Default.WarningAmber, null, tint = tone, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(tr(com.minimart.pos.R.string.recon_fmt, periodLabel), color = DT.OnSurface, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(
                    if (ok) Lang.t("${recon.matched.size} sale(s) matched to a payment. Nothing to fix.", ArabicCount.phrase(recon.matched.size, "لا مبيعات", "عملية بيع واحدة", "عمليتا بيع", "عمليات بيع", "عملية بيع") + " مطابقة لدفعة. لا شيء يحتاج إلى إصلاح.")
                    else Lang.t("${recon.problemCount} thing(s) to check", "${recon.problemCount} أمور تحتاج إلى مراجعة"),
                    color = tone, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                )
            }
            if (!ok) TextButton(onClick = { open = !open }) { Text(if (open) Lang.t("Hide", "إخفاء") else Lang.t("Show", "عرض"), color = DT.Teal) }
        }
        if (!ok && open) {
            if (recon.unclaimedPayments.isNotEmpty()) {
                ReconSection(Lang.t("Money received, no sale (${mpesaMoney(currency, recon.unclaimedTotal)})", "أموال مستلمة بلا عملية بيع (${mpesaMoney(currency, recon.unclaimedTotal)})"),
                    Lang.t("A customer paid but was not rung up, or the code was typed wrongly on a sale.", "دفع العميل لكن لم تُسجَّل عملية بيع، أو أُدخل الرمز بشكل خاطئ في عملية بيع."))
                recon.unclaimedPayments.take(8).forEach {
                    ReconLine("${it.code} · ${it.sender.ifBlank { Lang.t("unknown", "غير معروف") }}", mpesaMoney(currency, it.amount))
                }
                if (recon.unclaimedPayments.size > 8) ReconNote(Lang.t("+${recon.unclaimedPayments.size - 8} more", "+${recon.unclaimedPayments.size - 8} أخرى"))
            }
            if (recon.unverifiedSales.isNotEmpty()) {
                ReconSection(Lang.t("Sales with no payment message (${mpesaMoney(currency, recon.unverifiedTotal)})", "مبيعات بلا رسالة دفع (${mpesaMoney(currency, recon.unverifiedTotal)})"),
                    Lang.t("No message from M-Pesa was received for these codes on this phone. Check your M-Pesa balance before trusting them.", "لم تصل رسالة من إم-بيسا لهذه الرموز على هذا الهاتف. تحقق من رصيد إم-بيسا قبل الوثوق بها."))
                recon.unverifiedSales.take(8).forEach { ReconLine("${it.receipt} · ${it.code}", mpesaMoney(currency, it.saleTotal)) }
                if (recon.unverifiedSales.size > 8) ReconNote(Lang.t("+${recon.unverifiedSales.size - 8} more", "+${recon.unverifiedSales.size - 8} أخرى"))
            }
            if (recon.mismatches.isNotEmpty()) {
                ReconSection(Lang.t("Amount does not match", "المبلغ غير مطابق"), Lang.t("The payment and the sale total differ.", "الدفعة وإجمالي عملية البيع مختلفان."))
                recon.mismatches.take(8).forEach {
                    ReconLine("${it.sale.receipt} · ${Lang.t("sale", "بيع")} ${mpesaMoney(currency, it.sale.saleTotal)}",
                        "${Lang.t("paid", "مدفوع")} ${mpesaMoney(currency, it.payment.amount)}")
                }
            }
            if (recon.reusedCodes.isNotEmpty()) {
                ReconSection(Lang.t("Same code on several sales", "الرمز نفسه في عدة عمليات بيع"), Lang.t("One payment can only cover one sale.", "الدفعة الواحدة تغطي عملية بيع واحدة فقط."))
                recon.reusedCodes.forEach { (code, list) -> ReconLine(code, list.joinToString(", ") { it.receipt }) }
            }
        }
    }
}

@Composable private fun ReconSection(title: String, hint: String) {
    Spacer(Modifier.height(4.dp))
    Text(title, color = DT.OnSurface, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    Text(hint, color = DT.SubText, fontSize = 11.sp)
}
@Composable private fun ReconLine(left: String, right: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(left, color = DT.OnSurface, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(right, color = DT.Amber, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}
@Composable private fun ReconNote(text: String) = Text(text, color = DT.SubText, fontSize = 11.sp)

@Composable
private fun VerifyCard(check: MpesaCheck?, currency: String, onVerify: (String) -> Unit, onClear: () -> Unit) {
    var code by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(DT.Surface)
            .border(1.dp, DT.Border, RoundedCornerShape(18.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.VerifiedUser, null, tint = DT.Teal, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(tr(com.minimart.pos.R.string.verify_payment), color = DT.OnSurface, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        Text(tr(com.minimart.pos.R.string.mpesa_type_code),
            color = DT.SubText, fontSize = 12.sp)
        OutlinedTextField(
            value = code,
            onValueChange = { code = it.filter { c -> c.isLetterOrDigit() }.uppercase().take(12); if (check != null) onClear() },
            label = { Text(tr(com.minimart.pos.R.string.tx_code), color = DT.SubText) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onVerify(code) }),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = DT.Teal, unfocusedBorderColor = DT.Border,
                focusedTextColor = DT.OnSurface, unfocusedTextColor = DT.OnSurface, cursorColor = DT.Teal,
                focusedContainerColor = DT.Bg, unfocusedContainerColor = DT.Bg
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = { onVerify(code) }, enabled = code.length >= 8,
            modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp), shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = DT.Teal, contentColor = Color.White,
                disabledContainerColor = DT.Teal.copy(0.35f), disabledContentColor = Color.White.copy(0.7f))
        ) { Text(tr(com.minimart.pos.R.string.verify), fontWeight = FontWeight.Bold) }

        when (check) {
            is MpesaCheck.Found -> FeedbackBanner(
                Lang.t("Verified: ${mpesaMoney(currency, check.payment.amount)} from ${check.payment.senderName.ifBlank { check.payment.senderPhone }} at ${timeLabel(check.payment.receivedAt)}",
                    "تم التحقق: ${mpesaMoney(currency, check.payment.amount)} من ${check.payment.senderName.ifBlank { check.payment.senderPhone }} في ${timeLabel(check.payment.receivedAt)}"), isError = false)
            is MpesaCheck.NotFound -> FeedbackBanner(
                Lang.t("${check.code} was not received on this phone from M-Pesa. Don't release goods until it shows here.", "لم يصل ${check.code} من إم-بيسا إلى هذا الهاتف. لا تسلّم البضاعة حتى يظهر هنا."), isError = true)
            null -> {}
        }
    }
}

@Composable
private fun TotalCard(period: MpesaPeriod, totals: com.minimart.pos.ui.viewmodel.MpesaTotals?, currency: String, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(DT.Surface)
            .border(1.dp, DT.Border, RoundedCornerShape(16.dp)).padding(14.dp)
    ) {
        Text(period.label, color = DT.SubText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(mpesaMoney(currency, totals?.amount ?: 0.0), color = DT.Green, fontWeight = FontWeight.ExtraBold,
            fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val n = totals?.count ?: 0
        Text(ArabicCount.t(n, "payment", "payments", "لا دفعات", "دفعة واحدة", "دفعتان", "دفعات", "دفعة"), color = DT.SubText, fontSize = 11.sp)
    }
}

@Composable
private fun PaymentRow(p: MpesaPayment, currency: String, claimed: Boolean) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(DT.Surface)
            .border(1.dp, DT.Border, RoundedCornerShape(14.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(DT.Green.copy(0.15f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.CallReceived, null, tint = DT.Green, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(p.senderName.ifBlank { p.senderPhone.ifBlank { Lang.t("Unknown sender", "مرسل غير معروف") } }, color = DT.OnSurface,
                fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOf(p.code, p.senderPhone.takeIf { it.isNotBlank() && p.senderName.isNotBlank() }).filterNotNull().joinToString(" • "),
                color = DT.SubText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(timeLabel(p.receivedAt) + if (claimed) Lang.t("  •  matched to a sale", "  •  مطابقة لعملية بيع") else Lang.t("  •  not matched to a sale", "  •  غير مطابقة لعملية بيع"),
                color = if (claimed) DT.SubText else DT.Amber, fontSize = 11.sp)
        }
        Spacer(Modifier.width(8.dp))
        Text(mpesaMoney(currency, p.amount), color = DT.Green, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

private fun timeLabel(ms: Long): String = SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(ms))
