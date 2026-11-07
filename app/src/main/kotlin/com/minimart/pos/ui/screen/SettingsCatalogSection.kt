package com.minimart.pos.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minimart.pos.R
import com.minimart.pos.ui.theme.DT
import com.minimart.pos.ui.viewmodel.CloudUiState
import com.minimart.pos.ui.viewmodel.CloudViewModel

/** Chooses whether this shop sends or receives the shared product list, and runs it on demand. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CatalogShareBlock(cs: CloudUiState, vm: CloudViewModel) {
    Spacer(Modifier.height(14.dp))
    Text(tr(R.string.cat_title), color = DT.Ink, style = MaterialTheme.typography.titleSmall)
    Text(tr(R.string.cat_help), color = DT.SubText, fontSize = 11.sp, lineHeight = 15.sp)
    Spacer(Modifier.height(6.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(0 to R.string.cat_off, 1 to R.string.cat_main, 2 to R.string.cat_follow).forEach { (role, label) ->
            FilterChip(selected = cs.catalogRole == role, onClick = { vm.setCatalogRole(role) }, label = { Text(tr(label)) },
                modifier = Modifier.heightIn(min = 48.dp))
        }
    }
    if (cs.catalogRole == 2) Text(tr(R.string.cat_follow_warn), color = DT.Amber, fontSize = 11.sp, lineHeight = 15.sp)
    if (cs.catalogRole != 0) {
        Spacer(Modifier.height(6.dp))
        OutlinedButton(onClick = { vm.catalogNow() }, enabled = !cs.catalogBusy, modifier = Modifier.heightIn(min = 48.dp)) {
            if (cs.catalogBusy) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            else Text(tr(if (cs.catalogRole == 1) R.string.cat_publish_now else R.string.cat_get_now))
        }
        if (cs.catalogAt > 0) Text(tr(R.string.cat_last, java.text.SimpleDateFormat("d MMM, HH:mm", androidx.compose.ui.text.intl.Locale.current.platformLocale).format(java.util.Date(cs.catalogAt))),
            color = DT.SubText, fontSize = 11.sp)
    }
    cs.catalogMessage?.let { Text(it, color = if (cs.catalogError) DT.Red else DT.Green, fontSize = 12.sp) }
    if (cs.takeOverPrompt) {
        AlertDialog(onDismissRequest = { vm.dismissTakeOver() },
            title = { Text(tr(R.string.cat_takeover_title), color = DT.Ink) },
            text = { Text(tr(R.string.cat_takeover_body), color = DT.SubText) },
            confirmButton = { TextButton(onClick = { vm.catalogNow(takeOver = true) }) { Text(tr(R.string.cat_takeover_yes), color = DT.Teal) } },
            dismissButton = { TextButton(onClick = { vm.dismissTakeOver() }) { Text(tr(R.string.cancel), color = DT.SubText) } },
            containerColor = DT.Surface)
    }
}
