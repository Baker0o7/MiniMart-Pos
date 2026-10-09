package com.minimart.pos.display

import android.app.Presentation
import android.content.Context
import android.os.Bundle
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.minimart.pos.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class DisplayLine(val name: String, val qty: String, val total: Double)

data class CustomerDisplayState(
    val storeName: String = "",
    val currency: String = "KES",
    val lines: List<DisplayLine> = emptyList(),
    val subtotal: Double = 0.0,
    val discount: Double = 0.0,
    val total: Double = 0.0,
    val thankYou: Boolean = false,
    val change: Double = 0.0
)

/**
 * What the customer sees. The till pushes cart changes in here; the on-device "Customer view" and an
 * attached second display both just render this state.
 */
@Singleton
class CustomerDisplayHub @Inject constructor(settings: SettingsRepository) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(CustomerDisplayState())
    val state: StateFlow<CustomerDisplayState> = _state.asStateFlow()
    private var thanksJob: Job? = null

    init {
        scope.launch { settings.storeName.catch { }.collect { n -> _state.update { it.copy(storeName = n) } } }
        scope.launch { settings.currency.catch { }.collect { c -> _state.update { it.copy(currency = c) } } }
    }

    fun showCart(lines: List<DisplayLine>, subtotal: Double, discount: Double, total: Double) {
        if (lines.isEmpty() && _state.value.thankYou) return   // keep "Thank you" until it times out
        thanksJob?.cancel()
        _state.update { it.copy(lines = lines, subtotal = subtotal, discount = discount, total = total, thankYou = false, change = 0.0) }
    }

    fun showThanks(total: Double, change: Double) {
        thanksJob?.cancel()
        _state.update { it.copy(lines = emptyList(), subtotal = 0.0, discount = 0.0, total = total, thankYou = true, change = change) }
        thanksJob = scope.launch {
            delay(8_000)
            _state.update { it.copy(thankYou = false, total = 0.0, change = 0.0) }
        }
    }
}

private val Bg1 = Color(0xFF06231F)
private val Bg2 = Color(0xFF0A1830)
private val Teal = Color(0xFF2DD4BF)
private val Sub = Color(0xFF9FB4B0)

private fun money(cur: String, v: Double) = "$cur ${String.format(Locale.US, "%,.2f", v)}"

/** Full-screen customer-facing layout: big running total, readable lines, thank-you screen. */
@Composable
fun CustomerDisplayContent(s: CustomerDisplayState) {
    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Bg1, Bg2))).padding(28.dp)) {
        Column(Modifier.fillMaxSize()) {
            Text(s.storeName.ifBlank { "Welcome" }, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(16.dp))
            when {
                s.thankYou -> Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center) {
                    Icon(Icons.Default.CheckCircle, null, tint = Teal, modifier = Modifier.size(96.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("Thank you!", color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(8.dp))
                    Text("Paid ${money(s.currency, s.total)}", color = Sub, fontSize = 24.sp)
                    if (s.change > 0.005) {
                        Spacer(Modifier.height(18.dp))
                        Text("Your change", color = Sub, fontSize = 20.sp)
                        Text(money(s.currency, s.change), color = Teal, fontSize = 48.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
                s.lines.isEmpty() -> Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center) {
                    Text("Welcome", color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Your items will appear here", color = Sub, fontSize = 22.sp, textAlign = TextAlign.Center)
                }
                else -> {
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(s.lines) { l ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(l.name, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(l.qty, color = Sub, fontSize = 16.sp)
                                }
                                Text(money(s.currency, l.total), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Teal.copy(0.4f)))
                    Spacer(Modifier.height(12.dp))
                    if (s.discount > 0.005) {
                        Row(Modifier.fillMaxWidth()) {
                            Text("Discount", color = Sub, fontSize = 18.sp, modifier = Modifier.weight(1f))
                            Text("-${money(s.currency, s.discount)}", color = Teal, fontSize = 18.sp)
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("TOTAL", color = Sub, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text(money(s.currency, s.total), color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.ExtraBold,
                            maxLines = 1)
                    }
                }
            }
        }
    }
}

/** Shows [CustomerDisplayContent] on a secondary display (HDMI screen or a dual-screen POS's second panel). */
class CustomerPresentation(
    private val host: ComponentActivity,
    display: Display,
    private val hub: CustomerDisplayHub
) : Presentation(host as Context, display) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val decor = window?.decorView
        if (decor != null) {
            // A ComposeView needs these owners on its window; the Presentation window has none of its own.
            decor.setViewTreeLifecycleOwner(host)
            decor.setViewTreeViewModelStoreOwner(host)
            decor.setViewTreeSavedStateRegistryOwner(host)
        }
        setContentView(ComposeView(context).apply {
            setContent {
                val s by hub.state.collectAsState()
                CustomerDisplayContent(s)
            }
        })
    }
}
