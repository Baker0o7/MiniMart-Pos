package com.minimart.pos.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minimart.pos.cloud.MpesaWebhookClient
import com.minimart.pos.cloud.WebhookResult
import com.minimart.pos.ui.theme.DT
import com.minimart.pos.util.MpesaWebhook
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WebhookUi(val enabled: Boolean, val available: Boolean, val busy: Boolean = false, val url: String? = null,
                     val message: String? = null, val isError: Boolean = false)

@HiltViewModel
class MpesaWebhookViewModel @Inject constructor(private val client: MpesaWebhookClient) : ViewModel() {
    private val _state = MutableStateFlow(WebhookUi(client.enabled, client.available, url = client.callbackUrl()))
    val state: StateFlow<WebhookUi> = _state.asStateFlow()

    fun enable() = launchBusy { client.enable().let { r ->
        when (r) {
            is WebhookResult.Enabled -> _state.update { it.copy(enabled = true, url = r.callbackUrl, message = "Turned on. Register the address below with Safaricom.", isError = false) }
            is WebhookResult.Failed -> _state.update { it.copy(message = r.message, isError = true) }
            else -> {}
        }
    } }

    fun fetchNow() = launchBusy { client.pull().let { r ->
        when (r) {
            is WebhookResult.Pulled -> _state.update { it.copy(message = MpesaWebhook.summary(r.recorded, r.duplicates, r.rejected), isError = false) }
            is WebhookResult.Failed -> _state.update { it.copy(message = r.message, isError = true) }
            else -> {}
        }
    } }

    fun disable() { client.disable(); _state.update { it.copy(enabled = false, url = null, message = "Turned off. The old address no longer works for this phone.", isError = false) } }

    private fun launchBusy(block: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch { try { block() } finally { _state.update { it.copy(busy = false) } } }
    }
}

/** Owner-only: instant M-Pesa payments through Safaricom's Daraja C2B confirmation, via the owner's own Supabase project. */
@Composable
internal fun MpesaWebhookSettingsSection(vm: MpesaWebhookViewModel = hiltViewModel()) {
    val s by vm.state.collectAsState()
    val context = LocalContext.current
    DSection("Instant M-Pesa (webhook)", Icons.Default.Bolt, summary = if (s.enabled) "On" else "Off", initiallyExpanded = false) {
        Text("Safaricom tells your cloud project about each payment the moment it happens, and this phone picks it up within about 15 minutes, or at once when you open the M-Pesa list. It works even if the phone was off or had no SMS. Needs the Cloud dashboard connected and a Daraja C2B account (see cloud/mpesa_webhook.sql and the README).",
            color = DT.SubText, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(10.dp))
        if (!s.available) {
            Text("Connect the cloud dashboard first (the section above).", color = DT.Amber, style = MaterialTheme.typography.bodySmall)
        } else {
            if (s.enabled) {
                s.url?.let { url ->
                    Text("Address to register with Safaricom (Confirmation and Validation URL):", color = DT.OnSurface, style = MaterialTheme.typography.bodySmall)
                    SelectionContainer { Text(url, color = DT.TealLight, style = MaterialTheme.typography.bodySmall) }
                    OutlinedButton(onClick = {
                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Webhook address", url))
                    }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Copy address") }
                    Text("Keep this address private: anyone who has it can add payments to your list.", color = DT.SubText, style = MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
                    Button(onClick = vm::fetchNow, enabled = !s.busy, modifier = Modifier.heightIn(min = 48.dp)) { Text("Fetch now") }
                    OutlinedButton(onClick = vm::enable, enabled = !s.busy, modifier = Modifier.heightIn(min = 48.dp)) { Text("New address") }
                    TextButton(onClick = vm::disable, enabled = !s.busy, modifier = Modifier.heightIn(min = 48.dp)) { Text("Turn off", color = DT.Red) }
                }
            } else {
                Button(onClick = vm::enable, enabled = !s.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Turn on") }
            }
            if (s.busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp), color = DT.Teal)
        }
        s.message?.let { FeedbackBanner(it, isError = s.isError, modifier = Modifier.padding(top = 8.dp)) }
    }
}
