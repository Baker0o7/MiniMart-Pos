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
            Text(tr(com.minimart.pos.R.string.wiz_setup), color = DT.OnSurface, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = { vm.skip { onFinished(false) } }) { Text(tr(com.minimart.pos.R.string.skip), color = DT.SubText) }
        }
        LinearProgressIndicator(progress = { (step + 1) / 3f }, modifier = Modifier.fillMaxWidth(), color = DT.Teal, trackColor = DT.Border)
        Text(tr(com.minimart.pos.R.string.step_of, step + 1), color = DT.SubText, fontSize = 12.sp)

        when (step) {
            0 -> {
                Text(tr(com.minimart.pos.R.string.wiz_shop_called), color = DT.OnSurface, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(tr(com.minimart.pos.R.string.wiz_appears), color = DT.SubText, fontSize = 13.sp)
                OutlinedTextField(name, { name = it.take(60) }, label = { Text(tr(com.minimart.pos.R.string.shop_name)) }, singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Words, imeAction = androidx.compose.ui.text.input.ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                Text(tr(com.minimart.pos.R.string.currency), color = DT.OnSurface, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("KES", "UGX", "TZS", "USD").forEach { c ->
                        FilterChip(selected = currency == c, onClick = { currency = c }, label = { Text(c) })
                    }
                }
            }
            1 -> {
                Text(tr(com.minimart.pos.R.string.wiz_printer), color = DT.OnSurface, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(tr(com.minimart.pos.R.string.wiz_paper_help),
                    color = DT.SubText, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(58, 80).forEach { mm ->
                        FilterChip(selected = paper == mm, onClick = { paper = mm }, label = { Text("$mm mm") })
                    }
                }
                Text(tr(com.minimart.pos.R.string.wiz_no_printer), color = DT.SubText, fontSize = 12.sp)
            }
            else -> {
                Text(tr(com.minimart.pos.R.string.wiz_ready), color = DT.OnSurface, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(tr(com.minimart.pos.R.string.wiz_add_first),
                    color = DT.SubText, fontSize = 13.sp)
                Text(tr(com.minimart.pos.R.string.wiz_habits), color = DT.OnSurface, fontWeight = FontWeight.SemiBold)
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
            ) { Text(tr(com.minimart.pos.R.string.next), fontWeight = FontWeight.Bold) }
        } else {
            Button(
                onClick = { vm.finish(name, currency, paper) { onFinished(true) } },
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp), shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DT.Teal, contentColor = Color.White)
            ) { Text(tr(com.minimart.pos.R.string.wiz_add_products), fontWeight = FontWeight.Bold) }
            OutlinedButton(
                onClick = { vm.finish(name, currency, paper) { onFinished(false) } },
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp), shape = RoundedCornerShape(12.dp)
            ) { Text(tr(com.minimart.pos.R.string.wiz_go_home), color = DT.Teal) }
        }
        if (step > 0) TextButton(onClick = { step-- }) { Text(tr(com.minimart.pos.R.string.back), color = DT.SubText) }
    }
}
