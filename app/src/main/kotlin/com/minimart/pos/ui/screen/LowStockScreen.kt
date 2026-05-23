package com.minimart.pos.ui.screen

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.minimart.pos.data.entity.Product
import com.minimart.pos.ui.theme.DT
import com.minimart.pos.ui.viewmodel.ProductViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LowStockScreen(
    onBack: () -> Unit,
    vm: ProductViewModel = hiltViewModel()
) {
    val lowStock by vm.lowStockProducts.collectAsState()
    val context  = LocalContext.current

    Box(modifier = Modifier.fillMaxSize().background(DT.Bg)) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Top bar ───────────────────────────────────────────────────────
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = DT.Teal)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Reorder Reminders", color = DT.Teal, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    Text("${lowStock.size} item${if (lowStock.size != 1) "s" else ""} need restocking",
                        color = DT.SubText, style = MaterialTheme.typography.labelMedium)
                }
            }

            if (lowStock.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(64.dp), tint = DT.Green)
                        Text("All stock levels are healthy!", color = DT.OnSurface, fontWeight = FontWeight.SemiBold)
                        Text("No items below their reorder threshold.", color = DT.SubText, style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(lowStock, key = { it.id }) { product ->
                        LowStockCard(
                            product = product,
                            onCall = { phone ->
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                                context.startActivity(intent)
                            },
                            onWhatsApp = { phone ->
                                val msg = "Hello, I'd like to reorder ${product.name}. Current stock: ${product.stock}/${product.reorderQuantity} units needed."
                                val encoded = Uri.encode(msg)
                                val intent = Intent(Intent.ACTION_VIEW,
                                    Uri.parse("https://wa.me/$phone?text=$encoded"))
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                try { context.startActivity(intent) }
                                catch (_: Exception) {
                                    // WhatsApp not installed
                                    val fallback = Intent(Intent.ACTION_VIEW, Uri.parse("sms:$phone"))
                                    context.startActivity(fallback)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LowStockCard(
    product: Product,
    onCall: (String) -> Unit,
    onWhatsApp: (String) -> Unit
) {
    val stockPct = if (product.lowStockThreshold > 0)
        (product.stock.toFloat() / product.lowStockThreshold.toFloat()).coerceIn(0f, 1f)
    else 0f

    val urgencyColor = when {
        product.stock == 0 -> DT.Red
        product.stock <= product.lowStockThreshold / 2 -> DT.Amber
        else -> DT.Green.copy(alpha = 0.8f)
    }

    Box(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DT.Surface)
            .border(1.dp, DT.Border, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {

            // Product name + stock badge
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(product.name, color = DT.OnSurface, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("${product.category}  •  SKU: ${product.sku.ifBlank { "—" }}",
                        color = DT.SubText, style = MaterialTheme.typography.labelSmall)
                }
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(10.dp))
                        .background(urgencyColor.copy(0.15f))
                        .border(1.dp, urgencyColor.copy(0.4f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text("${product.stock} / ${product.lowStockThreshold}",
                        color = urgencyColor, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                }
            }

            // Stock progress bar
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LinearProgressIndicator(
                    progress = { stockPct },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = urgencyColor,
                    trackColor = DT.Border
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Current: ${product.stock} ${product.unit}", color = DT.SubText, style = MaterialTheme.typography.labelSmall)
                    Text("Threshold: ${product.lowStockThreshold}", color = DT.SubText, style = MaterialTheme.typography.labelSmall)
                }
            }

            // Reorder quantity
            if (product.reorderQuantity > 0) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Inventory2, null, tint = DT.Teal, modifier = Modifier.size(14.dp))
                    Text("Reorder ${product.reorderQuantity} ${product.unit}", color = DT.Teal,
                        style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                }
            }

            // Expiry warning
            if (product.expiryDate > 0L) {
                val daysLeft = ((product.expiryDate - System.currentTimeMillis()) / (1000 * 60 * 60 * 24)).toInt()
                val expiryColor = if (daysLeft < 0) DT.Red else if (daysLeft <= 7) DT.Amber else DT.SubText
                val label = if (daysLeft < 0) "⚠ Expired! ${-daysLeft} days ago"
                    else if (daysLeft == 0) "⚠ Expires today!"
                    else "Exp: ${java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date(product.expiryDate))} (${daysLeft}d)"
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.CalendarToday, null, tint = expiryColor, modifier = Modifier.size(14.dp))
                    Text(label, color = expiryColor, style = MaterialTheme.typography.labelSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                }
            }

            // Supplier info
            if (product.supplierName.isNotBlank() || product.supplierPhone.isNotBlank()) {
                HorizontalDivider(color = DT.Border)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Business, null, tint = DT.SubText, modifier = Modifier.size(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        if (product.supplierName.isNotBlank())
                            Text(product.supplierName, color = DT.OnSurface, fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodySmall)
                        if (product.supplierPhone.isNotBlank())
                            Text(product.supplierPhone, color = DT.SubText, style = MaterialTheme.typography.labelSmall)
                    }
                    if (product.supplierPhone.isNotBlank()) {
                        // Call button
                        IconButton(onClick = { onCall(product.supplierPhone) },
                            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(DT.TealDim)) {
                            Icon(Icons.Default.Phone, null, tint = DT.Teal, modifier = Modifier.size(18.dp))
                        }
                        // WhatsApp button
                        IconButton(onClick = { onWhatsApp(product.supplierPhone) },
                            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF25D366).copy(0.15f))) {
                            Icon(Icons.AutoMirrored.Filled.Chat, null, tint = Color(0xFF25D366), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Warning, null, tint = DT.Amber, modifier = Modifier.size(14.dp))
                    Text("No supplier info. Edit product to add supplier name & phone.",
                        color = DT.SubText, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
