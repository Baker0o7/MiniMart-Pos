package com.minimart.pos.ui.screen

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minimart.pos.ui.theme.DT
import com.minimart.pos.ui.viewmodel.HardwareViewModel
import com.minimart.pos.util.HealthCheck
import com.minimart.pos.util.HealthLevel

/** Printer, cash drawer, scanner and phone checks, with one-tap tests and shortcuts to the phone's settings. */
@Composable
fun HardwareScreen(onBack: () -> Unit, vm: HardwareViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Coming back from the phone's settings should show the new state.
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) vm.refresh() }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    fun open(intent: Intent) { runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    val appSettings = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))

    Column(Modifier.fillMaxSize().background(DT.Bg)) {
        GradientHeader("Hardware check", if (s.loading) "Checking…" else s.headline, onBack) {
            HeaderPillButton("Check again", Icons.Default.Refresh) { vm.refresh() }
        }
        if (s.loading && s.checks.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = DT.Teal) }
            return@Column
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(s.checks, key = { it.id }) { c ->
                DiagRow(c, testing = s.testing == c.id) {
                    when (c.id) {
                        "printer" -> if (c.detail.contains("Pair it again") || c.detail.contains("No printer chosen")) null else DiagButton("Test print") { vm.testPrinter() }
                        "drawer" -> DiagButton("Test drawer") { vm.testDrawer() }
                        "bluetooth" -> if (s.bluetoothOff) DiagButton("Bluetooth settings") { open(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
                                       else if (c.level == HealthLevel.BAD) DiagButton("Phone settings") { open(appSettings) } else null
                        "camera", "sms", "notifications" ->
                            if (c.level != HealthLevel.GOOD && !c.detail.startsWith("No camera")) DiagButton("Phone settings") { open(appSettings) } else null
                        "storage" -> if (c.level != HealthLevel.GOOD) DiagButton("Storage settings") { open(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)) } else null
                        else -> null
                    }
                }
            }
            item {
                Text("Tests send a short page to the printer and a signal to the drawer. Nothing is changed in your sales data.",
                    color = DT.SubText, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

private class DiagButton(val label: String, val onClick: () -> Unit)

@Composable
private fun DiagRow(check: HealthCheck, testing: Boolean, button: () -> DiagButton?) {
    val tone = healthColor(check.level)
    val word = when (check.level) { HealthLevel.GOOD -> "OK"; HealthLevel.WARN -> "Check"; HealthLevel.BAD -> "Fix" }
    val action = button()
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(DT.Surface)
            .border(1.dp, DT.Border, RoundedCornerShape(16.dp)).padding(14.dp)
            .semantics(mergeDescendants = true) { contentDescription = "${check.title}: $word. ${check.detail}" },
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(tone))
            Spacer(Modifier.width(10.dp))
            Text(check.title, color = DT.OnSurface, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text(word, color = tone, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
        }
        Text(check.detail, color = DT.SubText, fontSize = 13.sp)
        if (action != null) {
            OutlinedButton(onClick = action.onClick, enabled = !testing, shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, tone.copy(0.7f)), modifier = Modifier.heightIn(min = 48.dp)) {
                if (testing) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = tone)
                else Text(action.label, color = tone, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}
