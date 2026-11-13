package com.minimart.pos.ui.screen

import com.minimart.pos.ui.components.Text
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.launch
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.minimart.pos.R
import com.minimart.pos.ui.theme.DT
import com.minimart.pos.ui.viewmodel.SetupViewModel
import com.minimart.pos.util.BackupPassphrase
import com.minimart.pos.util.BackupResult
import com.minimart.pos.util.Lang
import com.minimart.pos.util.SetupWizard

/**
 * Four short steps shown once, to a brand-new shop: name it, set up receipts, make an encrypted backup that leaves the
 * phone, then start. The backup step cannot be skipped: a shop that loses its phone before ever backing up loses everything.
 * Looks like Settings (purple header, rounded outlined cards) so the first screen feels like the rest of the app.
 */
@OptIn(ExperimentalLayoutApi::class)
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

    // Backup step
    val scope = rememberCoroutineScope()
    var pass by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    var backupFile by remember { mutableStateOf<java.io.File?>(null) }
    var shareOpened by remember { mutableStateOf(false) }
    var confirmed by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var backupError by remember { mutableStateOf<String?>(null) }
    val passProblem = BackupPassphrase.problem(pass, again)
    val backupDone = SetupWizard.backupStepComplete(backupFile != null, shareOpened, confirmed)

    Column(Modifier.fillMaxSize().background(DT.Bg)) {
        GradientHeader(
            title = tr(R.string.wiz_setup), subtitle = tr(R.string.step_of, step + 1),
            onBack = { if (step > 0) step-- },
            actions = {
                // Skipping goes to the backup step, never past it.
                if (step < 2) TextButton(onClick = { step = 2 }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(tr(R.string.skip), color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            })
        LinearProgressIndicator(progress = { (step + 1) / 4f }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            color = DT.Teal, trackColor = DT.Border)

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (step) {
                0 -> {
                    WizCard(Icons.Default.Store, tr(R.string.wiz_shop_called)) {
                        Text(tr(R.string.wiz_appears), color = DT.SubText, fontSize = 12.sp)
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(name, { name = it.take(60) }, label = { Text(tr(R.string.shop_name)) }, singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Words),
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                    }
                    WizCard(Icons.Default.Payments, tr(R.string.currency)) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("KES", "UGX", "TZS", "USD").forEach { c ->
                                FilterChip(selected = currency == c, onClick = { currency = c }, label = { Text(c) }, modifier = Modifier.heightIn(min = 48.dp))
                            }
                        }
                    }
                }
                1 -> WizCard(Icons.Default.Print, tr(R.string.wiz_printer)) {
                    Text(tr(R.string.wiz_paper_help), color = DT.SubText, fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(58, 80).forEach { mm ->
                            FilterChip(selected = paper == mm, onClick = { paper = mm }, label = { Text("$mm mm") }, modifier = Modifier.heightIn(min = 48.dp))
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(tr(R.string.wiz_no_printer), color = DT.SubText, fontSize = 12.sp)
                }
                2 -> {
                    WizCard(Icons.Default.Backup, Lang.t("Back up your shop", "انسخ متجرك احتياطيًا")) {
                        Text(Lang.t("If this phone is lost, broken or stolen, a backup kept somewhere else is the only way to get your sales, stock and customers back. The file is locked with a passphrase you choose now, so only you can open it.",
                            "إذا فُقد هذا الهاتف أو تعطّل أو سُرق، فنسخة احتياطية محفوظة في مكان آخر هي الطريقة الوحيدة لاسترجاع مبيعاتك ومخزونك وعملائك. الملف مقفل بعبارة مرور تختارها الآن، ولا يفتحه غيرك."),
                            color = DT.SubText, fontSize = 13.sp)
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(pass, { pass = it; backupError = null }, enabled = backupFile == null,
                            label = { Text(Lang.t("Backup passphrase (8 or more characters)", "عبارة مرور النسخة (8 أحرف أو أكثر)")) }, singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(again, { again = it; backupError = null }, enabled = backupFile == null,
                            label = { Text(Lang.t("Type it again", "اكتبها مرة أخرى")) }, singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                        val hint = when {
                            backupFile != null -> null
                            pass.isEmpty() -> null
                            passProblem == BackupPassphrase.Problem.TOO_SHORT -> Lang.t("Use at least 8 characters.", "استخدم 8 أحرف على الأقل.")
                            passProblem == BackupPassphrase.Problem.TOO_SIMPLE -> Lang.t("Too easy to guess. Mix words, numbers or a sentence.", "سهلة التخمين. اجمع كلمات وأرقامًا أو جملة.")
                            passProblem == BackupPassphrase.Problem.MISMATCH && again.isNotEmpty() -> Lang.t("The two boxes are different.", "الخانتان مختلفتان.")
                            else -> null
                        }
                        if (hint != null) Text(hint, color = DT.Red, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                        backupError?.let { Text(it, color = DT.Red, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = {
                            val existing = backupFile
                            if (existing != null) { vm.shareBackup(existing); shareOpened = true }
                            else scope.launch {
                                working = true; backupError = null
                                when (val r = vm.createEncryptedBackup(pass)) {
                                    is BackupResult.Success -> { backupFile = r.file; vm.shareBackup(r.file); shareOpened = true }
                                    is BackupResult.Error -> backupError = r.message
                                }
                                working = false
                            }
                        }, enabled = !working && (backupFile != null || passProblem == null),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DT.Teal, contentColor = Color.White)
                        ) {
                            Text(when {
                                working -> Lang.t("Making the backup…", "جارٍ إنشاء النسخة…")
                                backupFile != null -> Lang.t("Share the file again", "شارك الملف مرة أخرى")
                                else -> Lang.t("Make backup and share it", "أنشئ النسخة وشاركها")
                            }, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (backupFile != null) WizCard(Icons.Default.CheckCircle, Lang.t("One last check", "تحقق أخير")) {
                        Text(Lang.t("Send the file to your email, Google Drive, WhatsApp or a flash disk: somewhere that is not this phone. Then write the passphrase down on paper. It cannot be recovered.",
                            "أرسل الملف إلى بريدك أو Google Drive أو واتساب أو فلاش: مكان ليس هذا الهاتف. ثم اكتب عبارة المرور على ورقة. لا يمكن استرجاعها."),
                            color = DT.SubText, fontSize = 13.sp)
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = confirmed, role = androidx.compose.ui.semantics.Role.Checkbox, onValueChange = { confirmed = it }),
                            verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = confirmed, onCheckedChange = null)
                            Spacer(Modifier.width(8.dp))
                            Text(Lang.t("The file is saved outside this phone and I wrote down my passphrase.", "الملف محفوظ خارج هذا الهاتف وكتبتُ عبارة المرور."),
                                color = DT.OnSurface, fontSize = 13.sp)
                        }
                    }
                }
                else -> {
                    WizCard(Icons.Default.CheckCircle, tr(R.string.wiz_ready)) {
                        Text(tr(R.string.wiz_add_first), color = DT.SubText, fontSize = 13.sp)
                    }
                    WizCard(Icons.Default.Star, tr(R.string.wiz_habits)) {
                        Habit(Icons.Default.Backup, tr(R.string.wiz_habit_backup))
                        Habit(Icons.Default.Schedule, tr(R.string.wiz_habit_shift))
                        Habit(Icons.Default.PhoneAndroid, tr(R.string.wiz_habit_mpesa))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // Buttons stay visible at the bottom, so the next step is always one thumb-tap away, even with the keyboard open.
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (step < 3) {
                Button(onClick = { step++ }, enabled = (step != 0 || nameOk) && (step != 2 || backupDone),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DT.Teal, contentColor = Color.White)
                ) { Text(tr(R.string.next), fontWeight = FontWeight.Bold) }
            } else {
                Button(onClick = { vm.finish(name, currency, paper) { onFinished(true) } },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DT.Teal, contentColor = Color.White)
                ) { Text(tr(R.string.wiz_add_products), fontWeight = FontWeight.Bold) }
                OutlinedButton(onClick = { vm.finish(name, currency, paper) { onFinished(false) } },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(14.dp)
                ) { Text(tr(R.string.wiz_go_home), color = DT.Teal) }
            }
        }
    }
}

/** A rounded outlined card with an icon and title: the same look as a Settings section, always open. */
@Composable
private fun WizCard(icon: ImageVector, title: String, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(DT.Surface).border(1.dp, DT.Border, RoundedCornerShape(18.dp))) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = DT.Teal, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(title, color = DT.OnSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.semantics { heading() })
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun Habit(icon: ImageVector, text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = DT.Teal, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, color = DT.OnSurface, fontSize = 13.sp, modifier = Modifier.weight(1f))
    }
}
