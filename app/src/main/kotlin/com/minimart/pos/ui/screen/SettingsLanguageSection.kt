package com.minimart.pos.ui.screen

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.minimart.pos.ui.theme.DT
import com.minimart.pos.util.AppLanguage

/** Lets the owner run the app in Arabic (right to left) or English, whatever language the phone uses. */
@Composable
internal fun LanguageSettingsSection() {
    val context = LocalContext.current
    var code by remember { mutableStateOf(AppLanguage.saved(context)) }
    @Composable fun label(c: String): String = if (c == AppLanguage.SYSTEM) tr(com.minimart.pos.R.string.language_follow_phone) else AppLanguage.options.first { it.first == c }.second
    DSection(tr(com.minimart.pos.R.string.language_title), Icons.Default.Language,
        summary = if (code == AppLanguage.SYSTEM) tr(com.minimart.pos.R.string.language_summary_phone) else label(code), initiallyExpanded = false) {
        Text(tr(com.minimart.pos.R.string.language_help), color = DT.SubText, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        Column(Modifier.selectableGroup()) {
            AppLanguage.options.forEach { (c, _) ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .selectable(selected = code == c, role = Role.RadioButton, onClick = {
                        if (code != c) {
                            AppLanguage.save(context, c); code = c
                            (context as? Activity)?.recreate()
                        }
                    }), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = code == c, onClick = null)
                    Spacer(Modifier.width(12.dp))
                    Text(label(c), color = DT.OnSurface, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}
