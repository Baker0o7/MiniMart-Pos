package com.minimart.pos.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.minimart.pos.data.entity.CreditTransaction
import com.minimart.pos.data.entity.Customer
import com.minimart.pos.ui.theme.DT
import com.minimart.pos.ui.viewmodel.CustomerViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CreditOverviewScreen(
    onBack: () -> Unit,
    vm: CustomerViewModel = hiltViewModel()
) {
    val state by vm.uiState.collectAsState()
    // Bug fix: was filter { creditBalance > 0 } — customers with NEGATIVE balances
    // (buy-on-account, i.e. they OWE money) were completely invisible in the Credit
    // Ledger. These are the most important ones to track. Now shows ALL customers with
    // a non-zero balance, sorted with debtors (negative) first so unpaid accounts are
    // always at the top of the list.
    val creditCustomers = state.customers
        .filter { it.creditBalance != 0.0 }
        .sortedWith(compareBy({ it.creditBalance >= 0 }, { -kotlin.math.abs(it.creditBalance) }))
    val totalCredit   = creditCustomers.filter { it.creditBalance > 0 }.sumOf { it.creditBalance }
    val totalOwed     = creditCustomers.filter { it.creditBalance < 0 }.sumOf { -it.creditBalance }
    val totalOutstanding = totalCredit  // positive wallet balances held on behalf of customers
    var expandedId by remember { mutableStateOf<Long?>(null) }
    var showAddCreditDialog by remember { mutableStateOf<Customer?>(null) }

    // Load transactions when expanding
    LaunchedEffect(expandedId) {
        expandedId?.let { vm.selectCustomer(state.customers.find { c -> c.id == it }) }
    }

    Box(modifier = Modifier.fillMaxSize().background(DT.Bg)) {
        LazyColumn(contentPadding = PaddingValues(bottom = 80.dp)) {

            // ── Top bar ───────────────────────────────────────────────────────
            item {
                Box(modifier = Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(DT.Teal, Color(0xFF004D40))))
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(40.dp).clip(CircleShape)
                            .background(Color.White.copy(0.18f))
                            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onBack),
                            contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Credit Ledger", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                            Text("${creditCustomers.size} customers with outstanding balance", color = Color.White.copy(0.7f), fontSize = 12.sp)
                        }
                    }
                }
            }

            // ── Summary card ──────────────────────────────────────────────────
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Total owed (buy-on-account / negative balances)
                    val debtors = creditCustomers.count { it.creditBalance < 0 }
                    Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(18.dp))
                        .background(Brush.verticalGradient(listOf(Color(0xFF1F0A0A), Color(0xFF0F0505))))
                        .border(1.dp, DT.Red.copy(0.3f), RoundedCornerShape(18.dp)).padding(14.dp)) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AccountBalanceWallet, null, tint = DT.Red, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Owed to Shop", color = DT.SubText, fontSize = 11.sp)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text("KES ${String.format("%,.2f", totalOwed)}",
                                color = DT.Red, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                            Text("$debtors customer${if (debtors != 1) "s" else ""}", color = DT.SubText, fontSize = 10.sp)
                        }
                    }
                    // All customers stats
                    Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(18.dp))
                        .background(Brush.verticalGradient(listOf(Color(0xFF0B2822), Color(0xFF061210))))
                        .border(1.dp, DT.Teal.copy(0.3f), RoundedCornerShape(18.dp)).padding(14.dp)) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.People, null, tint = DT.Teal, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("All Customers", color = DT.SubText, fontSize = 11.sp)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(state.customers.size.toString(),
                                color = DT.Teal, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp)
                            Text("KES ${String.format("%,.0f", state.customers.sumOf { it.totalPurchases })} total spent", color = DT.SubText, fontSize = 10.sp)
                        }
                    }
                }
            }

            // ── Empty state ───────────────────────────────────────────────────
            if (creditCustomers.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(60.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(60.dp), tint = DT.Green.copy(0.4f))
                            Text("No outstanding credit", color = DT.OnSurface, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                            Text("All customer accounts are settled", color = DT.SubText, fontSize = 13.sp)
                        }
                    }
                }
            }

            // ── Section header ────────────────────────────────────────────────
            if (creditCustomers.isNotEmpty()) {
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null, tint = DT.Amber, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Outstanding Balances", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }

            // ── Credit customer cards ─────────────────────────────────────────
            items(creditCustomers, key = { it.id }) { customer ->
                val isExpanded = expandedId == customer.id
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                    // Customer row
                    Box(modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 0.dp, bottomEnd = 0.dp))
                        .background(Brush.horizontalGradient(listOf(DT.Surface, DT.Surface2)))
                        .border(1.dp, if (isExpanded) DT.Teal.copy(0.4f) else DT.Border,
                            RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 0.dp, bottomEnd = 0.dp))
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                            expandedId = if (isExpanded) null else customer.id
                        }.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Avatar
                            Box(Modifier.size(46.dp).clip(CircleShape)
                                .background(Brush.linearGradient(listOf(DT.Teal, Color(0xFF004D40)))),
                                contentAlignment = Alignment.Center) {
                                Text(customer.name.firstOrNull()?.uppercase() ?: "?",
                                    color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(customer.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (customer.phone.isNotBlank())
                                    Text(customer.phone, color = DT.SubText, fontSize = 12.sp)
                                Text("${customer.visitCount} visits  •  Total spent KES ${String.format("%,.0f", customer.totalPurchases)}",
                                    color = DT.SubText, fontSize = 10.sp)
                            }
                            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                // Credit balance badge — green = customer has credit wallet,
                                // red = customer owes money (buy-on-account / negative balance)
                                val balanceColor = if (customer.creditBalance < 0) DT.Red else DT.Green
                                val balanceLabel = if (customer.creditBalance < 0)
                                    "OWES KES ${String.format("%.2f", -customer.creditBalance)}"
                                else
                                    "KES ${String.format("%.2f", customer.creditBalance)}"
                                Box(Modifier.clip(RoundedCornerShape(10.dp))
                                    .background(balanceColor.copy(0.15f))
                                    .border(1.dp, balanceColor.copy(0.4f), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 10.dp, vertical = 5.dp)) {
                                    Text(balanceLabel,
                                        color = balanceColor, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                                }
                                // Add credit button
                                Box(Modifier.size(28.dp).clip(CircleShape).background(DT.Teal.copy(0.15f))
                                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { showAddCreditDialog = customer },
                                    contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Add, null, tint = DT.Teal, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    // Expandable transactions
                    AnimatedVisibility(visible = isExpanded, enter = expandVertically(), exit = shrinkVertically()) {
                        val txs = state.transactions
                        Box(modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp))
                            .background(DT.Surface2)
                            .border(1.dp, DT.Teal.copy(0.3f), RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp))
                            .padding(12.dp)) {
                            if (txs.isEmpty()) {
                                Text("No transactions yet", color = DT.SubText, fontSize = 13.sp,
                                    modifier = Modifier.padding(8.dp))
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Recent Activity", color = DT.Teal, fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
                                    txs.take(5).forEach { tx -> CreditTxRow(tx) }
                                    if (txs.size > 5) {
                                        Text("+ ${txs.size - 5} more transactions",
                                            color = DT.SubText, fontSize = 11.sp,
                                            modifier = Modifier.padding(start = 4.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add credit dialog
    showAddCreditDialog?.let { customer ->
        AddCreditDialog(
            customer = customer,
            onDismiss = { showAddCreditDialog = null },
            onAdd = { amount, notes ->
                vm.addCredit(customer.id, amount, notes)
                showAddCreditDialog = null
            }
        )
    }
}

@Composable
private fun CreditTxRow(tx: CreditTransaction) {
    val df = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
    Row(modifier = Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(10.dp)).background(DT.Bg)
        .border(1.dp, DT.Border, RoundedCornerShape(10.dp)).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(32.dp).clip(CircleShape)
            .background(if (tx.amount > 0) DT.Green.copy(0.15f) else DT.Red.copy(0.12f)),
            contentAlignment = Alignment.Center) {
            Icon(if (tx.amount > 0) Icons.Default.Add else Icons.Default.ShoppingBag, null,
                tint = if (tx.amount > 0) DT.Green else DT.Red, modifier = Modifier.size(14.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(tx.type.name.replace("_", " "), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            if (tx.notes.isNotBlank()) Text(tx.notes, color = DT.SubText, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(df.format(Date(tx.createdAt)), color = DT.SubText, fontSize = 10.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("${if (tx.amount > 0) "+" else ""}KES ${String.format("%.2f", tx.amount)}",
                color = if (tx.amount > 0) DT.Green else DT.Red,
                fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text("Bal: KES ${String.format("%.2f", tx.balanceAfter)}", color = DT.SubText, fontSize = 10.sp)
        }
    }
}
