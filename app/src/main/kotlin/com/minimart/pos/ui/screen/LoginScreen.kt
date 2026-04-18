package com.minimart.pos.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.minimart.pos.ui.theme.DT
import com.minimart.pos.ui.viewmodel.AuthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    vm: AuthViewModel = hiltViewModel()
) {
    val state by vm.uiState.collectAsState()
    var username by remember { mutableStateOf("admin") }
    var pin by remember { mutableStateOf("") }
    var showPin by remember { mutableStateOf(false) }

    LaunchedEffect(state.isLoggedIn) { if (state.isLoggedIn) onLoginSuccess() }

    Box(
        modifier = Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(DT.Bg, Color(0xFF0D2420), DT.Bg))),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp).padding(top = 60.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Logo ──────────────────────────────────────────────────────────
            Box(
                modifier = Modifier.size(88.dp).clip(CircleShape)
                    .background(Brush.radialGradient(listOf(DT.Teal, Color(0xFF004D40)))),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.StoreMallDirectory, null, tint = Color.White, modifier = Modifier.size(48.dp))
            }
            Spacer(Modifier.height(20.dp))
            Text("MiniMart POS", color = DT.OnSurface, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp)
            Text("Sign in to continue", color = DT.SubText, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(32.dp))

            // ── Username ──────────────────────────────────────────────────────
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username", color = DT.SubText) },
                leadingIcon = { Icon(Icons.Default.Person, null, tint = DT.Teal, modifier = Modifier.size(20.dp)) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DT.Teal, unfocusedBorderColor = DT.Border,
                    focusedTextColor = DT.OnSurface, unfocusedTextColor = DT.OnSurface,
                    cursorColor = DT.Teal, focusedContainerColor = DT.Surface, unfocusedContainerColor = DT.Surface
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            // ── PIN field ─────────────────────────────────────────────────────
            OutlinedTextField(
                value = pin,
                onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) pin = it },
                label = { Text("PIN", color = DT.SubText) },
                leadingIcon = { Icon(Icons.Default.Lock, null, tint = DT.Teal, modifier = Modifier.size(20.dp)) },
                trailingIcon = {
                    IconButton(onClick = { showPin = !showPin }) {
                        Icon(if (showPin) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, tint = DT.SubText)
                    }
                },
                visualTransformation = if (showPin) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DT.Teal, unfocusedBorderColor = DT.Border,
                    focusedTextColor = DT.OnSurface, unfocusedTextColor = DT.OnSurface,
                    cursorColor = DT.Teal, focusedContainerColor = DT.Surface, unfocusedContainerColor = DT.Surface
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(20.dp))

            // ── PIN pad ───────────────────────────────────────────────────────
            DarkPinPad(
                onDigit = { d -> if (pin.length < 6) pin += d },
                onDelete = { if (pin.isNotEmpty()) pin = pin.dropLast(1) }
            )

            Spacer(Modifier.height(20.dp))

            state.error?.let {
                Text(it, color = DT.Red, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
            }

            // ── Login button ──────────────────────────────────────────────────
            Button(
                onClick = { vm.login(username, pin) },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                enabled = username.isNotBlank() && pin.length >= 4 && !state.isLoading,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DT.Teal, disabledContainerColor = DT.TealDim)
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                } else {
                    Text("Login", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun DarkPinPad(onDigit: (String) -> Unit, onDelete: () -> Unit) {
    val digits = listOf("1","2","3","4","5","6","7","8","9","","0","⌫")
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        digits.chunked(3).forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                row.forEach { d ->
                    if (d.isEmpty()) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        Box(
                            modifier = Modifier.weight(1f).aspectRatio(1.6f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(DT.Surface)
                                .border(1.dp, DT.Border, RoundedCornerShape(14.dp))
                                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                                    if (d == "⌫") onDelete() else onDigit(d)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(d, color = DT.TealLight, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}
