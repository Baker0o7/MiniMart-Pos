package com.minimart.pos.ui.screen

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.minimart.pos.ui.theme.DT
import com.minimart.pos.ui.viewmodel.AuthViewModel
import kotlinx.coroutines.delay

private const val LOCKOUT_SECONDS = 30
private const val MAX_ATTEMPTS = 3

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    vm: AuthViewModel = hiltViewModel()
) {
    val state   = vm.uiState.collectAsState().value
    val context = LocalContext.current

    var username       by remember { mutableStateOf("admin") }
    var pin            by remember { mutableStateOf("") }
    var attempts       by remember { mutableStateOf(0) }
    var lockedOut      by remember { mutableStateOf(false) }
    var lockoutSeconds by remember { mutableStateOf(LOCKOUT_SECONDS) }
    var showPin        by remember { mutableStateOf(false) }

    LaunchedEffect(lockedOut) {
        if (lockedOut) {
            lockoutSeconds = LOCKOUT_SECONDS
            while (lockoutSeconds > 0) { delay(1000); lockoutSeconds-- }
            lockedOut = false; attempts = 0; pin = ""
        }
    }
    LaunchedEffect(state.isLoggedIn) { if (state.isLoggedIn) onLoginSuccess() }
    LaunchedEffect(pin) {
        if (pin.length == 6) {
            kotlinx.coroutines.delay(50)
            vm.login(username, pin); pin = ""
        }
    }
    LaunchedEffect(state.error) {
        // Only increment on a genuine non-null error from a PIN attempt
        if (!state.error.isNullOrBlank() && !lockedOut) {
            attempts++
            if (attempts >= MAX_ATTEMPTS) lockedOut = true
        }
    }

    // Biometric
    fun launchBiometric() {
        try {
            val activity = context as? FragmentActivity ?: return
            val bio = BiometricManager.from(context)
            if (bio.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) != BiometricManager.BIOMETRIC_SUCCESS) return
            val executor = ContextCompat.getMainExecutor(context)
            val prompt = BiometricPrompt(activity, executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(r: BiometricPrompt.AuthenticationResult) {
                        vm.loginWithBiometric(username)
                    }
                })
            prompt.authenticate(BiometricPrompt.PromptInfo.Builder()
                .setTitle("Biometric Login").setSubtitle("Use fingerprint or face")
                .setNegativeButtonText("Use PIN").build())
        } catch (_: Exception) {}
    }
    LaunchedEffect(Unit) { launchBiometric() }

    Box(modifier = Modifier.fillMaxSize()
        .background(Brush.verticalGradient(listOf(Color(0xFF061510), Color(0xFF030A07), Color(0xFF020805))))) {

        // Background glow
        Box(modifier = Modifier.size(300.dp).offset(x = (-50).dp, y = (-50).dp)
            .clip(CircleShape).background(DT.Teal.copy(0.04f)).align(Alignment.TopStart))
        Box(modifier = Modifier.size(200.dp).offset(x = 50.dp, y = 80.dp)
            .clip(CircleShape).background(DT.Teal.copy(0.03f)).align(Alignment.BottomEnd))

        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {

            // Logo area
            Box(modifier = Modifier.size(90.dp).clip(RoundedCornerShape(26.dp))
                .background(Brush.linearGradient(listOf(DT.Teal, Color(0xFF004D40)))),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Default.ShoppingCart, null, tint = Color.White, modifier = Modifier.size(46.dp))
            }
            Spacer(Modifier.height(20.dp))
            Text("MiniMart POS", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp)
            Text("Point of Sale System", color = DT.SubText, fontSize = 14.sp, letterSpacing = 0.5.sp)
            Spacer(Modifier.height(36.dp))

            // Login card
            Box(modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF0D2420), Color(0xFF091A16))))
                .border(1.dp, DT.Teal.copy(0.2f), RoundedCornerShape(24.dp))
                .padding(24.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)) {

                    Text("Welcome Back 👋", color = Color.White,
                        fontWeight = FontWeight.Bold, fontSize = 18.sp)

                    // Username
                    OutlinedTextField(value = username, onValueChange = { username = it },
                        label = { Text("Username", color = DT.SubText) },
                        leadingIcon = { Icon(Icons.Default.Person, null, tint = DT.SubText) },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DT.Teal, unfocusedBorderColor = DT.Border,
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                            cursorColor = DT.Teal, focusedContainerColor = DT.Bg, unfocusedContainerColor = DT.Bg))

                    // PIN label + dots
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("PIN", color = DT.SubText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            TextButton(onClick = { showPin = !showPin },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                                Icon(if (showPin) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    null, tint = DT.SubText, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(if (showPin) "Hide" else "Show", color = DT.SubText, fontSize = 12.sp)
                            }
                        }
                        // PIN dots
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            repeat(6) { i ->
                                val filled = i < pin.length
                                Box(modifier = Modifier.size(42.dp).clip(RoundedCornerShape(12.dp))
                                    .background(if (filled) DT.Teal.copy(0.2f) else DT.Surface)
                                    .border(1.5.dp, if (filled) DT.Teal else DT.Border, RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center) {
                                    if (showPin && filled) {
                                        Text(pin[i].toString(), color = DT.Teal,
                                            fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                                    } else if (filled) {
                                        Box(Modifier.size(10.dp).clip(CircleShape).background(DT.Teal))
                                    }
                                }
                            }
                        }
                    }

                    // Lockout banner
                    AnimatedVisibility(visible = lockedOut) {
                        Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(DT.Red.copy(0.12f)).border(1.dp, DT.Red.copy(0.3f), RoundedCornerShape(12.dp))
                            .padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, null, tint = DT.Red, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Too many attempts. Wait ${lockoutSeconds}s", color = DT.Red, fontSize = 13.sp)
                        }
                    }

                    // Error banner
                    AnimatedVisibility(visible = state.error != null && !lockedOut) {
                        Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(DT.Red.copy(0.1f)).border(1.dp, DT.Red.copy(0.25f), RoundedCornerShape(12.dp))
                            .padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ErrorOutline, null, tint = DT.Red, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(state.error ?: "", color = DT.Red, fontSize = 12.sp)
                        }
                    }

                    // Attempts warning
                    if (attempts in 1 until MAX_ATTEMPTS && !lockedOut) {
                        Text("${MAX_ATTEMPTS - attempts} attempt${if (MAX_ATTEMPTS - attempts != 1) "s" else ""} remaining",
                            color = DT.Amber, fontSize = 12.sp)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Numeric keypad
            val keys = listOf("1","2","3","4","5","6","7","8","9","✓","0","⌫")
            Box(modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF0D1F1C).copy(0.6f))
                .border(1.dp, DT.Border.copy(0.5f), RoundedCornerShape(20.dp))
                .padding(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    keys.chunked(3).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { key ->
                                Box(modifier = Modifier.weight(1f).aspectRatio(1.6f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(when (key) {
                                        "✓"  -> DT.Green.copy(0.2f)
                                        "⌫" -> DT.Red.copy(0.12f)
                                        else -> DT.Surface
                                    })
                                    .border(if (key.isEmpty()) 0.dp else 1.dp,
                                        when (key) {
                                            "✓"  -> DT.Green.copy(0.5f)
                                            "⌫" -> DT.Red.copy(0.3f)
                                            else -> DT.Border
                                        }, RoundedCornerShape(14.dp))
                                    .clickable(enabled = key.isNotEmpty() && !lockedOut,
                                        indication = null, interactionSource = remember { MutableInteractionSource() }) {
                                        when (key) {
                                            "⌫" -> { if (pin.isNotEmpty()) pin = pin.dropLast(1) }
                                            "✓" -> { if (pin.isNotEmpty()) { vm.login(username, pin); pin = "" } }
                                            else -> { if (pin.length < 6) pin += key }
                                        }
                                    }, contentAlignment = Alignment.Center) {
                                    when (key) {
                                        "⌫" -> Icon(Icons.AutoMirrored.Filled.Backspace, null, tint = DT.Red, modifier = Modifier.size(20.dp))
                                        "✓" -> Icon(Icons.Default.Check, null, tint = DT.Green, modifier = Modifier.size(24.dp))
                                        else -> if (key.isNotEmpty()) Text(key, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, textAlign = TextAlign.Center)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Biometric + loading row
            Row(horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically) {
                if (state.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), color = DT.Teal, strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Signing in…", color = DT.SubText, fontSize = 13.sp)
                } else {
                    Box(modifier = Modifier.size(48.dp).clip(CircleShape)
                        .background(DT.Surface)
                        .border(1.dp, DT.Border, CircleShape)
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { launchBiometric() },
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Fingerprint, null, tint = DT.Teal, modifier = Modifier.size(26.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text("Use biometric", color = DT.SubText, fontSize = 13.sp)
                }
            }
        }
    }
}
