package com.minimart.pos.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minimart.pos.ui.theme.DT
import com.minimart.pos.ui.viewmodel.EndOfDayViewModel
import com.minimart.pos.util.EndOfDayStep
import com.minimart.pos.util.HealthAction
import com.minimart.pos.util.StepState

/** "Before you close the shop": what is finished, what still needs doing, and what blocks closing. */
@Composable
fun EndOfDayDialog(onDismiss: () -> Unit, onAction: (HealthAction) -> Unit, vm: EndOfDayViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refresh() }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DT.Surface,
        title = {
            Column {
                Text("End of day", color = DT.OnSurface, fontWeight = FontWeight.ExtraBold)
                if (!s.loading) Text(
                    when {
                        !s.canFinish -> "Something must be done before you close"
                        s.remaining == 0 -> "All done. The shop is ready to close."
                        else -> "${s.remaining} thing(s) left"
                    },
                    color = when { !s.canFinish -> DT.Red; s.remaining == 0 -> DT.Green; else -> DT.Amber },
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                )
            }
        },
        text = {
            if (s.loading) {
                Box(Modifier.fillMaxWidth().height(120.dp), Alignment.Center) { CircularProgressIndicator(color = DT.Teal) }
            } else {
                Column {
                    s.message?.let { FeedbackBanner(it, s.messageIsError); Spacer(Modifier.height(8.dp)) }
                    LazyColumn(Modifier.heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(s.steps, key = { it.id }) { step ->
                            StepRow(step, working = s.working,
                                onAction = { if (step.id == "backup") vm.backupNow() else { onDismiss(); onAction(step.action) } })
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = DT.Teal, fontWeight = FontWeight.Bold) } }
    )
}

@Composable
private fun StepRow(step: EndOfDayStep, working: Boolean, onAction: () -> Unit) {
    val tone = when (step.state) { StepState.DONE -> DT.Green; StepState.TODO -> DT.Amber; StepState.BLOCKED -> DT.Red }
    Surface(shape = RoundedCornerShape(12.dp), color = DT.Bg) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(when (step.state) { StepState.DONE -> "✓"; StepState.TODO -> "•"; StepState.BLOCKED -> "!" },
                    color = tone, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, modifier = Modifier.width(22.dp))
                Text(step.title, color = DT.OnSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Text(step.detail, color = DT.SubText, fontSize = 12.sp)
            if (step.state != StepState.DONE && (step.action != HealthAction.NONE || step.id == "backup")) {
                val label = if (step.id == "backup") (if (working) "Backing up…" else "Back up now") else actionLabel(step.action) ?: "Open"
                TextButton(onClick = onAction, enabled = !working) { Text(label, color = DT.Teal, fontWeight = FontWeight.Bold) }
            }
        }
    }
}
