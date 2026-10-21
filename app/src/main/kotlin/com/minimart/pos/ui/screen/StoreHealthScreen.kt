package com.minimart.pos.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minimart.pos.ui.theme.DT
import com.minimart.pos.ui.viewmodel.StoreHealthViewModel
import com.minimart.pos.util.BackupAdvice
import com.minimart.pos.util.HealthAction
import com.minimart.pos.util.HealthCheck
import com.minimart.pos.util.HealthLevel

internal fun healthColor(level: HealthLevel): Color = when (level) {
    HealthLevel.GOOD -> DT.Green
    HealthLevel.WARN -> DT.Amber
    HealthLevel.BAD -> DT.Red
}

internal fun actionLabel(a: HealthAction): String? = when (a) {
    HealthAction.NONE -> null
    HealthAction.BACKUP -> "Back up"
    HealthAction.SYNC -> "Open sync"
    HealthAction.MPESA -> "Open M-Pesa"
    HealthAction.LOW_STOCK -> "See products"
    HealthAction.SETTINGS -> "Open settings"
    HealthAction.DATA_CHECK -> "Check again"
    HealthAction.SHIFTS -> "Open shifts"
    HealthAction.USERS -> "Open users"
}

/** One screen that says whether the shop's data and phone are in good shape, and how to fix what is not. */
@Composable
fun StoreHealthScreen(
    onBack: () -> Unit,
    onAction: (HealthAction) -> Unit,
    vm: StoreHealthViewModel = hiltViewModel()
) {
    val s by vm.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().background(DT.Bg)) {
        GradientHeader("Store health", if (s.loading) "Checking…" else s.headline, onBack) {
            HeaderPillButton("Check again", Icons.Default.Refresh) { vm.refresh() }
        }
        if (s.loading && s.checks.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = DT.Teal) }
            return@Column
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            s.error?.let { item { FeedbackBanner(it, isError = true) } }
            if (s.checks.isNotEmpty()) item { OverallBanner(s.overall, s.headline, s.checkedAt) }
            items(s.checks, key = { it.id }) { c ->
                HealthRow(c, if (c.id == "data") s.dataProblems else emptyList()) {
                    when (c.action) {
                        HealthAction.DATA_CHECK -> vm.refresh()
                        HealthAction.NONE -> {}
                        else -> onAction(c.action)
                    }
                }
            }
            item {
                Text("This check only reads your data. It never changes anything.",
                    color = DT.SubText, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun OverallBanner(level: HealthLevel, headline: String, checkedAt: Long) {
    val tone = healthColor(level)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(tone.copy(0.14f))
            .border(1.dp, tone.copy(0.6f), RoundedCornerShape(18.dp)).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(if (level == HealthLevel.GOOD) Icons.Default.CheckCircle else if (level == HealthLevel.WARN) Icons.Default.WarningAmber else Icons.Default.ErrorOutline,
            null, tint = tone, modifier = Modifier.size(30.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(headline, color = DT.OnSurface, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            if (checkedAt > 0) Text("Checked ${BackupAdvice.ago(checkedAt, System.currentTimeMillis())}", color = DT.SubText, fontSize = 12.sp)
        }
    }
}

@Composable
private fun HealthRow(check: HealthCheck, extra: List<String>, onAction: () -> Unit) {
    val tone = healthColor(check.level)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(DT.Surface)
            .border(1.dp, DT.Border, RoundedCornerShape(16.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(tone))
            Spacer(Modifier.width(10.dp))
            Text(check.title, color = DT.OnSurface, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text(when (check.level) { HealthLevel.GOOD -> "OK"; HealthLevel.WARN -> "Check"; HealthLevel.BAD -> "Fix" },
                color = tone, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
        }
        Text(check.detail, color = DT.SubText, fontSize = 13.sp)
        extra.forEach { Text("• $it", color = DT.Red, fontSize = 12.sp) }
        actionLabel(check.action)?.let { label ->
            if (check.level != HealthLevel.GOOD) {
                OutlinedButton(onClick = onAction, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, tone.copy(0.7f))) {
                    Text(label, color = tone, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}
