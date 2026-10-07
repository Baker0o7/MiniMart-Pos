package com.minimart.pos.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
    if (value % 1.0 == 0.0) value.toLong().toString() else String.format("%.2f", value)

/** Editable text for a number: 12.50 -> "12.5", 100.0 -> "100" (no stray trailing zeros). */
internal fun plainNumber(value: Double, maxDecimals: Int = 2): String {
    val s = String.format(java.util.Locale.US, "%.${maxDecimals}f", value)
    return if ('.' in s) s.trimEnd('0').trimEnd('.') else s
}

/** "SUPPLIER" -> "Supplier", "PETTY_CASH" -> "Petty cash". */
internal fun prettyEnumName(name: String): String =
    name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

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
            .background(Brush.verticalGradient(listOf(DT.Teal, Color(0xFF006B5E), Color(0xFF004D40))))
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(42.dp).clip(CircleShape)
                    .background(Color.White.copy(0.18f))
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) {
                    Text(subtitle, color = Color.White.copy(0.78f), fontSize = 12.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            actions()
        }
    }
}

/** Compact pill-shaped action for a [GradientHeader] (e.g. "Add"). */
@Composable
internal fun HeaderPillButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier.height(42.dp).clip(RoundedCornerShape(21.dp))
            .background(Color.White.copy(0.2f))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(if (isError) Icons.Default.ErrorOutline else Icons.Default.CheckCircle, null,
            tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(message, color = tint, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
    }
}
