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
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
    var showPin        by remember { mutableStateOf(false) }
    // Bug fix: lockout used to be local `remember {}` state, wiped on process death —
    // force-closing the app trivially reset the "3 failed attempts" lockout. Now sourced
    // from AuthViewModel, which persists it via DataStore (see SettingsRepository).
    val lockedOut      = state.isLockedOut
    val lockoutSeconds = state.lockoutRemainingSeconds

    LaunchedEffect(state.isLoggedIn) { if (state.isLoggedIn) onLoginSuccess() }
    LaunchedEffect(pin) {
        if (pin.length == 6) {
            kotlinx.coroutines.delay(50)
            vm.login(username, pin); pin = ""
        }
    }
    LaunchedEffect(lockedOut) { if (lockedOut) pin = "" }

    // Biometric
    fun launchBiometric() {
        // Bug fix: previously showed the prompt for ANY device with biometric hardware
        // enrolled, regardless of whether any account had opted in — and on success,
        // logged in as whatever username happened to be typed in the field. Now gated
        // on a specific user having explicitly bound biometric login via Settings.
        if (!state.biometricEnabled) return
        try {
            val activity = context as? FragmentActivity ?: return
            val bio = BiometricManager.from(context)
            if (bio.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) != BiometricManager.BIOMETRIC_SUCCESS) return
            val executor = ContextCompat.getMainExecutor(context)
            val prompt = BiometricPrompt(activity, executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(r: BiometricPrompt.AuthenticationResult) {
                        vm.loginWithBiometric()
                    }
                })
            prompt.authenticate(BiometricPrompt.PromptInfo.Builder()
                .setTitle("Biometric Login").setSubtitle("Use fingerprint or face")
                .setNegativeButtonText("Use PIN").build())
        } catch (_: Exception) {}
    }
    LaunchedEffect(state.biometricEnabled) { if (state.biometricEnabled) launchBiometric() }


    // Emerald "glass" palette for this screen
    val bgTop   = Color(0xFF04201A)
    val bgMid   = Color(0xFF031612)
    val bgBot   = Color(0xFF020E0B)
    val emerald = Color(0xFF1DE9A6)
    val emDeep  = Color(0xFF0A7F63)
    val glassHi = Color(0xFF0F4338)
    val glassLo = Color(0xFF0A2E27)
    val glassBd = Color(0xFF1E7A63)

    Box(modifier = Modifier.fillMaxSize()
        .background(Brush.verticalGradient(listOf(bgTop, bgMid, bgBot)))) {

        // Decorative arcs: top-left and bottom
        Box(modifier = Modifier.size(280.dp).offset(x = (-150).dp, y = (-170).dp)
            .clip(CircleShape).background(emerald.copy(0.05f))
            .border(1.5.dp, emerald.copy(0.35f), CircleShape).align(Alignment.TopStart))
        Box(modifier = Modifier.size(520.dp).offset(x = (-60).dp, y = 380.dp)
            .clip(CircleShape).background(emerald.copy(0.04f))
            .border(1.5.dp, emerald.copy(0.25f), CircleShape).align(Alignment.BottomStart))

        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {

            // Logo tile with glow
            Box(modifier = Modifier.size(92.dp)
                .shadow(24.dp, RoundedCornerShape(28.dp), ambientColor = emerald, spotColor = emerald)
                .clip(RoundedCornerShape(28.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF16C79A), Color(0xFF07654F))))
                .border(1.5.dp, emerald.copy(0.6f), RoundedCornerShape(28.dp)),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Default.ShoppingCart, null, tint = Color.White, modifier = Modifier.size(48.dp))
            }
            Spacer(Modifier.height(18.dp))
            Row {
                Text("MiniMart ", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp)
                Text("POS", color = emerald, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp)
            }
            Text("Point of Sale System", color = Color.White.copy(0.9f), fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))
            Box(Modifier.width(44.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(emerald))
            Spacer(Modifier.height(22.dp))

            // Login card
            Box(modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF0B3B31), Color(0xFF082A24))))
                .border(1.2.dp, emerald.copy(0.45f), RoundedCornerShape(26.dp))
                .padding(horizontal = 18.dp, vertical = 20.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {

                    Text("Welcome Back 👋", color = Color.White,
                        fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)

                    // Username (pill)
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Username", color = emerald, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 6.dp))
                        OutlinedTextField(value = username, onValueChange = { username = it },
                            leadingIcon = { Icon(Icons.Default.Person, null, tint = emerald) },
                            singleLine = true, modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(50),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = emerald, unfocusedBorderColor = glassBd,
                                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                                cursorColor = emerald,
                                focusedContainerColor = Color(0xFF051C17), unfocusedContainerColor = Color(0xFF051C17)))
                    }

                    // PIN label + boxes
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("PIN", color = Color.White.copy(0.85f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(start = 6.dp))
                            TextButton(onClick = { showPin = !showPin },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                                Icon(if (showPin) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    null, tint = emerald, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(if (showPin) "Hide" else "Show", color = emerald, fontSize = 13.sp)
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            repeat(6) { i ->
                                val filled = i < pin.length
                                Box(modifier = Modifier.weight(1f).aspectRatio(0.95f).clip(RoundedCornerShape(14.dp))
                                    .background(if (filled) emerald.copy(0.18f) else Color(0xFF082923))
                                    .border(1.5.dp, if (filled) emerald else glassBd, RoundedCornerShape(14.dp)),
                                    contentAlignment = Alignment.Center) {
                                    if (showPin && filled) {
                                        Text(pin[i].toString(), color = emerald,
                                            fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                                    } else if (filled) {
                                        Box(Modifier.size(11.dp).clip(CircleShape).background(emerald))
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
                    if (state.failedAttempts in 1 until MAX_ATTEMPTS && !lockedOut) {
                        val remaining = MAX_ATTEMPTS - state.failedAttempts
                        Text("$remaining attempt${if (remaining != 1) "s" else ""} remaining",
                            color = DT.Amber, fontSize = 12.sp)
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            // Numeric keypad — glass keys, bright ✓ and red ⌫
            val keys = listOf("1","2","3","4","5","6","7","8","9","✓","0","⌫")
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                keys.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { key ->
                            val keyBrush = when (key) {
                                "✓"  -> Brush.verticalGradient(listOf(Color(0xFF2BE08A), Color(0xFF0E8A4E)))
                                "⌫" -> Brush.verticalGradient(listOf(Color(0xFF5A1414), Color(0xFF330A0A)))
                                else -> Brush.verticalGradient(listOf(glassHi, glassLo))
                            }
                            val keyBorder = when (key) {
                                "✓"  -> Color(0xFF6BF5B0)
                                "⌫" -> Color(0xFFE5484D)
                                else -> glassBd
                            }
                            Box(modifier = Modifier.weight(1f).aspectRatio(1.75f)
                                .clip(RoundedCornerShape(18.dp))
                                .background(keyBrush)
                                .border(1.2.dp, keyBorder.copy(0.8f), RoundedCornerShape(18.dp))
                                .clickable(enabled = !lockedOut,
                                    indication = null, interactionSource = remember { MutableInteractionSource() }) {
                                    when (key) {
                                        "⌫" -> { if (pin.isNotEmpty()) pin = pin.dropLast(1) }
                                        "✓" -> { if (pin.isNotEmpty()) { vm.login(username, pin); pin = "" } }
                                        else -> { if (pin.length < 6) pin += key }
                                    }
                                }, contentAlignment = Alignment.Center) {
                                when (key) {
                                    "⌫" -> Icon(Icons.AutoMirrored.Filled.Backspace, null, tint = Color(0xFFFF6B6B), modifier = Modifier.size(28.dp))
                                    "✓" -> Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(34.dp))
                                    else -> Text(key, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 30.sp, textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Biometric + loading row
            Row(horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically) {
                if (state.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), color = emerald, strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Signing in…", color = Color.White.copy(0.7f), fontSize = 13.sp)
                } else if (state.biometricEnabled) {
                    Box(modifier = Modifier.size(48.dp).clip(CircleShape)
                        .background(glassLo)
                        .border(1.dp, glassBd, CircleShape)
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { launchBiometric() },
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Fingerprint, null, tint = emerald, modifier = Modifier.size(26.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text("Use biometric", color = Color.White.copy(0.7f), fontSize = 13.sp)
                }
            }
        }
    }
}
