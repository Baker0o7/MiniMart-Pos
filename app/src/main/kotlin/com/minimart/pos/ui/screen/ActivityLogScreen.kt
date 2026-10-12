package com.minimart.pos.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.minimart.pos.ui.theme.DT
import com.minimart.pos.ui.viewmodel.ActivityEntry
import com.minimart.pos.ui.viewmodel.ActivityFilter
import com.minimart.pos.ui.viewmodel.ActivityLogViewModel

/** Owner-only: who did what, newest first. */
@Composable
fun ActivityLogScreen(onBack: () -> Unit, vm: ActivityLogViewModel = hiltViewModel()) {
    val s by vm.state.collectAsState()
    Column(Modifier.fillMaxSize().background(DT.Bg)) {
        GradientHeader("Activity log", "Who did what", onBack) {
            HeaderPillButton("Refresh", Icons.Default.Refresh) { vm.refresh() }
        }
        s.check?.let { c ->
            val good = c.ok
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(12.dp))
                    .background((if (good) DT.Green else DT.Red).copy(0.12f))
                    .border(1.dp, (if (good) DT.Green else DT.Red).copy(0.5f), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Text(
                    if (good) "Log intact · ${c.entries} protected entries"
                    else "Log was altered: the record breaks at line ${c.brokenAtLine}. Entries before it are still trustworthy.",
                    color = if (good) DT.Green else DT.Red, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                )
            }
        }
        androidx.compose.foundation.lazy.LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(ActivityFilter.values().toList()) { f ->
                FilterChip(selected = s.filter == f, onClick = { vm.setFilter(f) }, label = { Text(f.label) })
            }
        }
        Spacer(Modifier.height(8.dp))
        val rows = s.visible
        if (s.loading) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = DT.Teal) }
        } else if (rows.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Nothing recorded here yet.", color = DT.SubText) }
        } else {
            LazyColumn(
                state = rememberLazyListState(), contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(rows) { ActivityRow(it) }
            }
        }
    }
}

@Composable
private fun ActivityRow(e: ActivityEntry) {
    val tone = when {
        e.event.startsWith("SALE_VOID") || e.event.startsWith("SALE_REFUND") || e.event == "LOGIN_FAILED" || e.event == "BACKUP_FAILED" || e.event == "NO_SALE" -> DT.Red
        e.event.startsWith("PRODUCT") || e.event == "STOCK_ADJUSTED" || e.event == "DISCOUNT_APPLIED" -> DT.Amber
        else -> DT.Teal
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(DT.Surface)
            .border(1.dp, DT.Border, RoundedCornerShape(14.dp)).padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(prettyEnumName(e.event), color = tone, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text(e.time.substringAfter(' ').take(5) + " · " + e.time.substringBefore(' ').drop(5), color = DT.SubText, fontSize = 11.sp)
        }
        if (e.user.isNotBlank()) Text(e.user, color = DT.OnSurface, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        if (e.detail.isNotBlank()) Text(e.detail, color = DT.SubText, fontSize = 12.sp)
    }
}
