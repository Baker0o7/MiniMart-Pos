package com.minimart.pos.ui.screen

import androidx.compose.foundation.background
import com.minimart.pos.util.Lang
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import com.minimart.pos.ui.components.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minimart.pos.ui.theme.DT

// ─── Shared building blocks for the sale / product / expense screens ──────────────────────

/**
 * Keeps a numeric field to digits and a single decimal point. A comma (some keypads) becomes a
 * point. Pasted text such as "NaN" or "1e9" used to parse as a number.
 */
internal fun sanitizeDecimalInput(raw: String, maxDecimals: Int = 2, maxWhole: Int = 9): String {
    val cleaned = raw.replace(',', '.').filter { it.isDigit() || it == '.' }
    val dot = cleaned.indexOf('.')
    if (dot < 0) return cleaned.take(maxWhole)
    val whole = cleaned.substring(0, dot).take(maxWhole)
    val fraction = cleaned.substring(dot + 1).replace(".", "").take(maxDecimals)
    return "$whole.$fraction"
}

/** Money field: digits plus at most two decimals. */
internal fun sanitizeMoneyInput(raw: String): String = sanitizeDecimalInput(raw, maxDecimals = 2)

/** "50" for whole amounts, "49.50" otherwise — `%.0f` used to round 12.50 up to 13. */
internal fun formatPrice(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else String.format(java.util.Locale.US, "%.2f", value)

/** Editable text for a number: 12.50 -> "12.5", 100.0 -> "100" (no stray trailing zeros). */
internal fun plainNumber(value: Double, maxDecimals: Int = 2): String {
    val s = String.format(java.util.Locale.US, "%.${maxDecimals}f", value)
    return if ('.' in s) s.trimEnd('0').trimEnd('.') else s
}

/** "SUPPLIER" -> "Supplier", "PETTY_CASH" -> "Petty cash". */
/** Readable name for an enum value (payment method, expense category, order status, activity event); Arabic when known. */
internal fun prettyEnumName(name: String): String =
    (if (com.minimart.pos.util.Lang.arabic) ENUM_AR[name.uppercase()] else null)
        ?: name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

private val ENUM_AR = mapOf(
    "DRAFT" to "مسودة",
    "ORDERED" to "تم الطلب",
    "PARTIAL" to "مستلم جزئيًا",
    "RECEIVED" to "مستلم",
    "CANCELLED" to "ملغى",
    "SUPPLIER" to "مورد",
    "ELECTRICITY" to "كهرباء",
    "WATER" to "ماء",
    "RENT" to "إيجار",
    "SALARY" to "رواتب",
    "TRANSPORT" to "نقل",
    "PACKAGING" to "تغليف",
    "CLEANING" to "تنظيف",
    "MAINTENANCE" to "صيانة",
    "TAXES" to "ضرائب",
    "OTHER" to "أخرى",
    "CASH" to "نقدًا",
    "MPESA" to "M-Pesa",
    "CARD" to "بطاقة",
    "MIXED" to "مختلط",
    "CREDIT" to "آجل",
    "COMPLETED" to "مكتملة",
    "REFUNDED" to "مستردة",
    "VOIDED" to "ملغاة",
    "LOGIN_SUCCESS" to "تسجيل دخول ناجح",
    "LOGIN_FAILED" to "فشل تسجيل الدخول",
    "LOGOUT" to "تسجيل خروج",
    "SALE_COMPLETED" to "بيع مكتمل",
    "SALE_VOIDED" to "إلغاء بيع",
    "SALE_REFUNDED" to "استرجاع بيع",
    "SYNC_CONFLICT" to "تعارض مزامنة",
    "DISCOUNT_APPLIED" to "تطبيق خصم",
    "CREDIT_ADDED" to "إضافة رصيد",
    "CREDIT_USED" to "استخدام رصيد",
    "USER_CREATED" to "إنشاء مستخدم",
    "USER_DELETED" to "حذف مستخدم",
    "PIN_CHANGED" to "تغيير الرمز السري",
    "PRODUCT_CREATED" to "إضافة منتج",
    "PRODUCT_EDITED" to "تعديل منتج",
    "PRODUCT_DELETED" to "حذف منتج",
    "STOCK_ADJUSTED" to "تعديل المخزون",
    "NO_SALE" to "فتح الدرج بدون بيع",
    "SHIFT_OPENED" to "فتح وردية",
    "SHIFT_CLOSED" to "إغلاق وردية",
    "BACKUP_CREATED" to "إنشاء نسخة احتياطية",
    "BACKUP_FAILED" to "فشل النسخ الاحتياطي",
    "BACKUP_RESTORED" to "استعادة نسخة احتياطية",
    "DATA_CHECK" to "فحص البيانات",
    "PERMISSION_DENIED" to "رفض الإذن",
    "SETTINGS_CHANGED" to "تغيير الإعدادات",
    "SESSION_EXPIRED" to "انتهاء الجلسة",
    "LOG_ROTATED" to "تدوير السجل",
)

/**
 * Rounded-bottom gradient header used by the main screens: back button, title, optional subtitle
 * and any number of trailing actions.
 */
@Composable
internal fun GradientHeader(
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Box(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .background(Brush.verticalGradient(listOf(DT.Teal, DT.HeaderMid, DT.HeaderEnd)))
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape)
                    .background(Color.White.copy(0.18f))
                    .clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = Lang.t("Go back", "رجوع"), onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() })
                if (subtitle != null) {
                    Text(subtitle, color = Color.White.copy(0.78f), fontSize = 12.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            actions()
        }
    }
}

/** Round icon-only action for a [GradientHeader] (e.g. "+"); [label] is the accessibility description. */
@Composable
internal fun HeaderPillButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(48.dp).clip(CircleShape)
            .background(Color.White.copy(0.2f))
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

/** Inline success / error strip. Renders nothing when [message] is null. */
@Composable
internal fun FeedbackBanner(message: String?, isError: Boolean, modifier: Modifier = Modifier) {
    if (message == null) return
    val tint = if (isError) DT.Red else DT.Green
    Row(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(tint.copy(0.14f))
            .padding(horizontal = 12.dp, vertical = 10.dp)
            // Read out when it appears, so a screen-reader user hears the result of what they just did.
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(if (isError) Icons.Default.ErrorOutline else Icons.Default.CheckCircle, null,
            tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(message, color = tint, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
    }
}


/** A short "Removed X — UNDO" strip. */
@androidx.compose.runtime.Composable
internal fun UndoBar(message: String, onUndo: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(DT.Amber.copy(0.14f))
            .padding(start = 12.dp, end = 4.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(message, color = DT.OnSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f))
        androidx.compose.material3.TextButton(onClick = onUndo) {
            Text(tr(com.minimart.pos.R.string.undo), color = DT.Amber, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
        }
    }
}

/** Search fields: show a Search key on the keyboard and close the keyboard when it is pressed. */
fun searchKeyboardOptions() = androidx.compose.foundation.text.KeyboardOptions(
    imeAction = androidx.compose.ui.text.input.ImeAction.Search)

@androidx.compose.runtime.Composable
fun searchKeyboardActions(): androidx.compose.foundation.text.KeyboardActions {
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    return androidx.compose.foundation.text.KeyboardActions(onSearch = { focus.clearFocus() })
}

/**
 * Shown once after the app closed unexpectedly. Sharing sends the saved report through whatever app the owner
 * picks (WhatsApp, email…); nothing is sent automatically.
 */
@Composable
internal fun CrashReportPrompt() {
    val context = LocalContext.current
    var report by remember { mutableStateOf(com.minimart.pos.util.CrashRecorder.unseen(context)) }
    val current = report ?: return
    AlertDialog(
        onDismissRequest = { com.minimart.pos.util.CrashRecorder.markSeen(context, current); report = null },
        containerColor = DT.Surface,
        title = { Text(tr(com.minimart.pos.R.string.crash_title), color = DT.OnSurface, fontWeight = FontWeight.Bold) },
        text = {
            Text(tr(com.minimart.pos.R.string.crash_body),
                color = DT.SubText, fontSize = 13.sp)
        },
        confirmButton = {
            TextButton(onClick = {
                val text = try { current.readText() } catch (_: Exception) { "" }
                val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_SUBJECT, "MiniMart POS crash report")
                    putExtra(android.content.Intent.EXTRA_TEXT, text)
                }
                try { context.startActivity(android.content.Intent.createChooser(send, "Send crash details").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
                com.minimart.pos.util.CrashRecorder.markSeen(context, current); report = null
            }) { Text(tr(com.minimart.pos.R.string.share), color = DT.Teal, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = { com.minimart.pos.util.CrashRecorder.markSeen(context, current); report = null }) {
                Text(tr(com.minimart.pos.R.string.dismiss), color = DT.SubText)
            }
        }
    )
}


/**
 * The one "nothing here yet" look used by the lists: a quiet icon, a plain title, an optional hint and an optional
 * main action. Text stays at full contrast and the button at the 48dp touch size, so every empty list reads the same.
 */
@Composable
internal fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Box(modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
        Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally, verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
            Icon(icon, null, modifier = Modifier.size(56.dp), tint = DT.SubText.copy(0.6f))
            Text(title, color = DT.OnSurface, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            if (hint != null) Text(hint, color = DT.SubText, style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            if (actionLabel != null && onAction != null) {
                Button(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DT.Teal, contentColor = androidx.compose.ui.graphics.Color.White)) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(actionLabel, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
            }
        }
    }
}
