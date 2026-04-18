package com.minimart.pos.ui.screen

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.minimart.pos.ui.theme.Brand500
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

    LaunchedEffect(state.isLoggedIn) {
        if (state.isLoggedIn) onLoginSuccess()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Logo
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .clip(CircleShape)
                        .background(Brand500),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.StoreMallDirectory,
                        null,
                        tint = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.size(50.dp)
                    )
                }

                Text("MiniMart POS", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Sign in to continue", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    leadingIcon = { Icon(Icons.Default.Person, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) pin = it },
                    label = { Text("PIN") },
                    leadingIcon = { Icon(Icons.Default.Lock, null) },
                    trailingIcon = {
                        IconButton(onClick = { showPin = !showPin }) {
                            Icon(if (showPin) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                        }
                    },
                    visualTransformation = if (showPin) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                // PIN pad for quick entry
                PinPad(
                    onDigit = { d -> if (pin.length < 6) pin += d },
                    onDelete = { if (pin.isNotEmpty()) pin = pin.dropLast(1) }
                )

                Button(
                    onClick = { vm.login(username, pin) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    enabled = username.isNotBlank() && pin.length >= 4 && !state.isLoading,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("Login", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun PinPad(onDigit: (String) -> Unit, onDelete: () -> Unit) {
    val digits = listOf("1","2","3","4","5","6","7","8","9","","0","⌫")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        digits.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { d ->
                    if (d.isEmpty()) {
                        Spacer(Modifier.size(72.dp))
                    } else {
                        OutlinedButton(
                            onClick = { if (d == "⌫") onDelete() else onDigit(d) },
                            modifier = Modifier.size(72.dp),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(d, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}
