package com.minimart.pos.ui.screen

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
    val enabled by vm.trackingEnabled.collectAsState()
    val currency by vm.currency.collectAsState()
    val period by vm.period.collectAsState()
    val payments by vm.payments.collectAsState()
    val totals by vm.totals.collectAsState()
    val check by vm.check.collectAsState()

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
                title = "M-Pesa Payments",
                subtitle = if (enabled && permitted) "Tracking incoming payments" else "Tracking is off",
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
                                Text("Log payments automatically", color = DT.OnSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(
                                    when {
                                        denied -> "SMS permission was refused. Allow it in phone Settings › Apps › MiniMart POS."
                                        enabled && !permitted -> "Needs SMS permission to read M-Pesa confirmations."
                                        else -> "Reads M-Pesa confirmation texts on this phone. Texts from ordinary numbers are ignored."
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
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = DT.Teal, uncheckedTrackColor = DT.Border)
                            )
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
                                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { vm.setPeriod(p) }
                                        .padding(horizontal = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) { Text(p.label, color = if (sel) Color.White else DT.SubText, fontSize = 13.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium) }
                            }
                        }
                    }
                    if (payments.isEmpty()) {
                        item {
                            Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.PhoneAndroid, null, tint = DT.SubText.copy(0.4f), modifier = Modifier.size(52.dp))
                                Spacer(Modifier.height(8.dp))
                                Text(if (enabled && permitted) "No M-Pesa payments yet for ${period.label.lowercase()}" else "Turn on tracking to start logging payments",
                                    color = DT.SubText, fontSize = 13.sp)
                            }
                        }
                    } else {
                        items(payments, key = { it.id }) { PaymentRow(it, currency) }
                    }
                }
            }
        }
    }
}

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
            Text("Verify a payment", color = DT.OnSurface, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        Text("Type the transaction code from the customer's message. It is only confirmed if this phone received it from M-Pesa itself.",
            color = DT.SubText, fontSize = 12.sp)
        OutlinedTextField(
            value = code,
            onValueChange = { code = it.filter { c -> c.isLetterOrDigit() }.uppercase().take(12); if (check != null) onClear() },
            label = { Text("Transaction code, e.g. UJ71ABC234", color = DT.SubText) },
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
        ) { Text("Verify", fontWeight = FontWeight.Bold) }

        when (check) {
            is MpesaCheck.Found -> FeedbackBanner(
                "Verified: ${mpesaMoney(currency, check.payment.amount)} from ${check.payment.senderName.ifBlank { check.payment.senderPhone }} " +
                    "at ${timeLabel(check.payment.receivedAt)}", isError = false)
            is MpesaCheck.NotFound -> FeedbackBanner(
                "${check.code} was not received on this phone from M-Pesa. Don't release goods until it shows here.", isError = true)
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
        Text("$n ${if (n == 1) "payment" else "payments"}", color = DT.SubText, fontSize = 11.sp)
    }
}

@Composable
private fun PaymentRow(p: MpesaPayment, currency: String) {
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
            Text(p.senderName.ifBlank { p.senderPhone.ifBlank { "Unknown sender" } }, color = DT.OnSurface,
                fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOf(p.code, p.senderPhone.takeIf { it.isNotBlank() && p.senderName.isNotBlank() }).filterNotNull().joinToString(" • "),
                color = DT.SubText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(timeLabel(p.receivedAt), color = DT.SubText, fontSize = 11.sp)
        }
        Spacer(Modifier.width(8.dp))
        Text(mpesaMoney(currency, p.amount), color = DT.Green, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

private fun timeLabel(ms: Long): String = SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(ms))
