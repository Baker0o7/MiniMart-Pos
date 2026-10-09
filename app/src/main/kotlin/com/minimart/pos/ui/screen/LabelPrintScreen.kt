package com.minimart.pos.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.minimart.pos.ui.theme.DT
import com.minimart.pos.ui.viewmodel.LabelPrintViewModel

@Composable
fun LabelPrintScreen(onBack: () -> Unit, vm: LabelPrintViewModel = hiltViewModel()) {
    val s by vm.uiState.collectAsState()
    Column(Modifier.fillMaxSize().background(DT.Bg)) {
        GradientHeader("Barcode labels", "Thermal printer · ${s.totalLabels} selected", onBack)
        FeedbackBanner(s.message, s.isError, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

        OutlinedTextField(
            value = s.query, onValueChange = vm::setQuery, singleLine = true,
            label = { Text("Search products", fontSize = 12.sp) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = DT.OnSurface, unfocusedTextColor = DT.OnSurface,
                focusedBorderColor = DT.Teal, unfocusedBorderColor = DT.Border,
                focusedLabelColor = DT.TealLight, unfocusedLabelColor = DT.SubText, cursorColor = DT.TealLight)
        )
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(selected = s.showPrice, onClick = { vm.setShowPrice(!s.showPrice) }, label = { Text("Show price") })
            Spacer(Modifier.width(8.dp))
            AssistChip(onClick = vm::selectLowStock, label = { Text("Low stock") })
            Spacer(Modifier.width(8.dp))
            if (s.totalLabels > 0) AssistChip(onClick = vm::clearSelection, label = { Text("Clear") })
        }

        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (s.visible.isEmpty()) item { Text("No products with a barcode found.", color = DT.SubText) }
            items(s.visible, key = { it.id }) { p ->
                val n = s.copies[p.id] ?: 0
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(DT.Surface)
                        .border(1.dp, if (n > 0) DT.Teal else DT.Border, RoundedCornerShape(14.dp)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(p.name, color = DT.OnSurface, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(p.barcode, color = DT.SubText, fontSize = 12.sp, maxLines = 1)
                    }
                    IconButton(onClick = { vm.setCopies(p.id, n - 1) }, enabled = n > 0) {
                        Icon(Icons.Default.Remove, "Fewer labels for ${p.name}", tint = if (n > 0) DT.TealLight else DT.SubText)
                    }
                    Text(n.toString(), color = DT.OnSurface, fontWeight = FontWeight.Bold, modifier = Modifier.widthIn(min = 22.dp))
                    IconButton(onClick = { vm.setCopies(p.id, n + 1) }) {
                        Icon(Icons.Default.Add, "More labels for ${p.name}", tint = DT.TealLight)
                    }
                }
            }
        }

        Box(Modifier.fillMaxWidth().background(DT.Surface).padding(12.dp)) {
            Button(
                onClick = vm::print,
                enabled = s.totalLabels > 0 && !s.printing,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DT.Teal)
            ) {
                Icon(Icons.Default.Print, null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text(if (s.printing) "Printing…" else "Print ${s.totalLabels} label${if (s.totalLabels == 1) "" else "s"}", fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}
