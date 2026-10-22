package com.minimart.pos.ui.screen

import android.bluetooth.BluetoothDevice
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minimart.pos.data.entity.UserRole
import com.minimart.pos.data.repository.SettingsRepository
import com.minimart.pos.printer.PrintResult
import com.minimart.pos.printer.ThermalPrinter
import com.minimart.pos.ui.theme.DT
import com.minimart.pos.ui.viewmodel.SyncViewModel
import com.minimart.pos.util.RoleManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch


// ─── Helpers ─────────────────────────────────────────────────────────────────

@Composable
internal fun DSection(
    title: String,
    icon: ImageVector,
    summary: String? = null,
    initiallyExpanded: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    // Remembered per title so a rotation doesn't snap every section back to its default.
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(DT.Surface)
        .border(1.dp, DT.Border, RoundedCornerShape(18.dp))) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .clickable(
                        indication = null,
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        role = androidx.compose.ui.semantics.Role.Button,
                        onClickLabel = if (expanded) "Collapse $title" else "Expand $title"
                    ) { expanded = !expanded }
                    .semantics(mergeDescendants = true) { stateDescription = if (expanded) "Expanded" else "Collapsed" },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, null, tint = DT.Teal, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, color = DT.OnSurface, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                        modifier = Modifier.semantics { heading() })
                    if (!expanded && summary != null) {
                        Text(summary, color = DT.SubText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    if (expanded) "Collapse" else "Expand", tint = DT.SubText, modifier = Modifier.size(22.dp))
            }
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)) { content() }
            }
        }
    }
}

@Composable
internal fun DField(value: String, onValueChange: (String) -> Unit, label: String, icon: ImageVector? = null, numeric: Boolean = false) {
    OutlinedTextField(value = value, onValueChange = onValueChange,
        label = { Text(label, color = DT.SubText, style = MaterialTheme.typography.labelSmall) },
        leadingIcon = icon?.let { { Icon(it, null, tint = DT.SubText, modifier = Modifier.size(18.dp)) } },
        keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
        singleLine = true, modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp), colors = dColors())
}

/** Full-width save button that is only live when something changed and confirms with "✓ Saved". */
@Composable
internal fun SaveButton(label: String, enabled: Boolean, color: Color, icon: ImageVector? = null, onSave: () -> Unit) {
    var saved by remember { mutableStateOf(false) }
    LaunchedEffect(saved) { if (saved) { kotlinx.coroutines.delay(2500); saved = false } }
    Button(
        onClick = { onSave(); saved = true },
        enabled = enabled || saved,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color, contentColor = Color.White,
            disabledContainerColor = color.copy(0.35f), disabledContentColor = Color.White.copy(0.7f)
        )
    ) {
        if (!saved && icon != null) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(if (saved) "✓ Saved" else label, fontWeight = FontWeight.Bold)
    }
}

@Composable internal fun SSubTitle(text: String) {
    Text(text, color = DT.SubText, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 4.dp))
}

@Composable internal fun HDivider() = HorizontalDivider(color = DT.Border, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))

@Composable internal fun DMenuRow(label: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(label, color = color, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Icon(Icons.Default.ChevronRight, null, tint = DT.SubText, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable internal fun dColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = DT.Teal, unfocusedBorderColor = DT.Border,
    focusedTextColor = DT.OnSurface, unfocusedTextColor = DT.OnSurface,
    cursorColor = DT.Teal, focusedContainerColor = DT.Bg, unfocusedContainerColor = DT.Bg
)
