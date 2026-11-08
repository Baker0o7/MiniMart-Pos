package com.minimart.pos.ui.screen

import com.minimart.pos.ui.components.Text
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.minimart.pos.R
import com.minimart.pos.ui.theme.DT
import com.minimart.pos.ui.viewmodel.SetupViewModel
import com.minimart.pos.util.SetupWizard

/**
 * Three short steps shown once, to a brand-new shop: name it, set up receipts, then start.
 * Looks like Settings (purple header, rounded outlined cards) so the first screen feels like the rest of the app.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SetupWizardScreen(onFinished: (openProducts: Boolean) -> Unit, vm: SetupViewModel = hiltViewModel()) {
    var step by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("KES") }
    var paper by remember { mutableIntStateOf(58) }
    LaunchedEffect(Unit) {
        val existing = vm.currentStoreName()
        if (!SetupWizard.isDefaultStoreName(existing)) name = existing
    }
    val nameOk = SetupWizard.validStoreName(name)

    Column(Modifier.fillMaxSize().background(DT.Bg)) {
        GradientHeader(
            title = tr(R.string.wiz_setup), subtitle = tr(R.string.step_of, step + 1),
            onBack = { if (step > 0) step-- else vm.skip { onFinished(false) } },
            actions = {
                TextButton(onClick = { vm.skip { onFinished(false) } }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(tr(R.string.skip), color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            })
        LinearProgressIndicator(progress = { (step + 1) / 3f }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            color = DT.Teal, trackColor = DT.Border)

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (step) {
                0 -> {
                    WizCard(Icons.Default.Store, tr(R.string.wiz_shop_called)) {
                        Text(tr(R.string.wiz_appears), color = DT.SubText, fontSize = 12.sp)
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(name, { name = it.take(60) }, label = { Text(tr(R.string.shop_name)) }, singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Words),
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                    }
                    WizCard(Icons.Default.Payments, tr(R.string.currency)) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("KES", "UGX", "TZS", "USD").forEach { c ->
                                FilterChip(selected = currency == c, onClick = { currency = c }, label = { Text(c) }, modifier = Modifier.heightIn(min = 48.dp))
                            }
                        }
                    }
                }
                1 -> WizCard(Icons.Default.Print, tr(R.string.wiz_printer)) {
                    Text(tr(R.string.wiz_paper_help), color = DT.SubText, fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(58, 80).forEach { mm ->
                            FilterChip(selected = paper == mm, onClick = { paper = mm }, label = { Text("$mm mm") }, modifier = Modifier.heightIn(min = 48.dp))
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(tr(R.string.wiz_no_printer), color = DT.SubText, fontSize = 12.sp)
                }
                else -> {
                    WizCard(Icons.Default.CheckCircle, tr(R.string.wiz_ready)) {
                        Text(tr(R.string.wiz_add_first), color = DT.SubText, fontSize = 13.sp)
                    }
                    WizCard(Icons.Default.Star, tr(R.string.wiz_habits)) {
                        Habit(Icons.Default.Backup, tr(R.string.wiz_habit_backup))
                        Habit(Icons.Default.Schedule, tr(R.string.wiz_habit_shift))
                        Habit(Icons.Default.PhoneAndroid, tr(R.string.wiz_habit_mpesa))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // Buttons stay visible at the bottom, so the next step is always one thumb-tap away, even with the keyboard open.
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (step < 2) {
                Button(onClick = { step++ }, enabled = step != 0 || nameOk,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DT.Teal, contentColor = Color.White)
                ) { Text(tr(R.string.next), fontWeight = FontWeight.Bold) }
            } else {
                Button(onClick = { vm.finish(name, currency, paper) { onFinished(true) } },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DT.Teal, contentColor = Color.White)
                ) { Text(tr(R.string.wiz_add_products), fontWeight = FontWeight.Bold) }
                OutlinedButton(onClick = { vm.finish(name, currency, paper) { onFinished(false) } },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(14.dp)
                ) { Text(tr(R.string.wiz_go_home), color = DT.Teal) }
            }
        }
    }
}

/** A rounded outlined card with an icon and title: the same look as a Settings section, always open. */
@Composable
private fun WizCard(icon: ImageVector, title: String, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(DT.Surface).border(1.dp, DT.Border, RoundedCornerShape(18.dp))) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = DT.Teal, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(title, color = DT.OnSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.semantics { heading() })
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun Habit(icon: ImageVector, text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = DT.Teal, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, color = DT.OnSurface, fontSize = 13.sp, modifier = Modifier.weight(1f))
    }
}
