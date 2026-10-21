package com.minimart.pos.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.minimart.pos.ui.theme.DT
import com.minimart.pos.ui.viewmodel.SetupViewModel
import com.minimart.pos.util.SetupWizard

/** Three short steps shown once, to a brand-new shop: name it, set up receipts, then start. */
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

    Column(Modifier.fillMaxSize().background(DT.Bg).padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Set up your shop", color = DT.OnSurface, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = { vm.skip { onFinished(false) } }) { Text("Skip", color = DT.SubText) }
        }
        LinearProgressIndicator(progress = { (step + 1) / 3f }, modifier = Modifier.fillMaxWidth(), color = DT.Teal, trackColor = DT.Border)
        Text("Step ${step + 1} of 3", color = DT.SubText, fontSize = 12.sp)

        when (step) {
            0 -> {
                Text("What is your shop called?", color = DT.OnSurface, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("It appears on receipts and reports.", color = DT.SubText, fontSize = 13.sp)
                OutlinedTextField(name, { name = it.take(60) }, label = { Text("Shop name") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                Text("Currency", color = DT.OnSurface, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("KES", "UGX", "TZS", "USD").forEach { c ->
                        FilterChip(selected = currency == c, onClick = { currency = c }, label = { Text(c) })
                    }
                }
            }
            1 -> {
                Text("Receipt printer", color = DT.OnSurface, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Pick the paper width of your thermal printer. Most small printers use 58 mm; larger ones use 80 mm. You can pair the printer later in Settings.",
                    color = DT.SubText, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(58, 80).forEach { mm ->
                        FilterChip(selected = paper == mm, onClick = { paper = mm }, label = { Text("$mm mm") })
                    }
                }
                Text("No printer? Skip this: receipts can also be shared as PDF.", color = DT.SubText, fontSize = 12.sp)
            }
            else -> {
                Text("You're ready", color = DT.OnSurface, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Add your products first, by scanning barcodes or typing them in. Then open a shift and make your first sale.",
                    color = DT.SubText, fontSize = 13.sp)
                Text("Good habits from day one:", color = DT.OnSurface, fontWeight = FontWeight.SemiBold)
                Text("• Make a backup every day (Store health reminds you).\n• Close your shift each evening and count the drawer.\n• Turn on M-Pesa tracking to match payments to sales.",
                    color = DT.SubText, fontSize = 13.sp)
            }
        }

        Spacer(Modifier.height(8.dp))
        if (step < 2) {
            Button(
                onClick = { step++ }, enabled = step != 0 || nameOk,
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp), shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DT.Teal, contentColor = Color.White)
            ) { Text("Next", fontWeight = FontWeight.Bold) }
        } else {
            Button(
                onClick = { vm.finish(name, currency, paper) { onFinished(true) } },
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp), shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DT.Teal, contentColor = Color.White)
            ) { Text("Add my products", fontWeight = FontWeight.Bold) }
            OutlinedButton(
                onClick = { vm.finish(name, currency, paper) { onFinished(false) } },
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp), shape = RoundedCornerShape(12.dp)
            ) { Text("Go to home", color = DT.Teal) }
        }
        if (step > 0) TextButton(onClick = { step-- }) { Text("Back", color = DT.SubText) }
    }
}
