package com.minimart.pos.ui.screen

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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

private const val MAX_ATTEMPTS = 3
private const val LOCKOUT_SECONDS = 30

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    vm: AuthViewModel = hiltViewModel()
) {
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current

    var username by remember { mutableStateOf("admin") }
    var pin by remember { mutableStateOf("") }
    var attempts by remember { mutableIntStateOf(0) }
    var lockedOut by remember { mutableStateOf(false) }
    var lockoutSeconds by remember { mutableIntStateOf(LOCKOUT_SECONDS) }
    var biometricError by remember { mutableStateOf<String?>(null) }

    // Track failed attempts
    LaunchedEffect(state.error) {
        if (state.error != null && !state.isLoggedIn && !lockedOut) {
            attempts++
            if (attempts >= MAX_ATTEMPTS) lockedOut = true
        }
    }

    // Lockout countdown
    LaunchedEffect(lockedOut) {
        if (lockedOut) {
            lockoutSeconds = LOCKOUT_SECONDS
            while (lockoutSeconds > 0) { delay(1000); lockoutSeconds-- }
            lockedOut = false; attempts = 0; pin = ""
        }
    }

    LaunchedEffect(state.isLoggedIn) {
        if (state.isLoggedIn) onLoginSuccess()
    }

    // Auto-submit when 6 digits entered - small delay prevents race with VM
    LaunchedEffect(pin) {
        if (pin.length == 6) {
            kotlinx.coroutines.delay(50)
            vm.login(username, pin)
            pin = ""
        }
    }

    // Auto-show biometric on load
    LaunchedEffect(Unit) {
        delay(400)
        if (isBiometricAvailable(context)) {
            triggerBiometric(context, username, vm) { biometricError = it }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(DT.Bg, Color(0xFF0D2420), DT.Bg))),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp).padding(top = 56.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Logo
            Box(
                modifier = Modifier.size(88.dp).clip(CircleShape)
                    .background(Brush.radialGradient(listOf(DT.Teal, Color(0xFF004D40)))),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.StoreMallDirectory, null, tint = Color.White, modifier = Modifier.size(48.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text("MiniMart POS", color = DT.OnSurface, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp)
            Text("Sign in to continue", color = DT.SubText, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(28.dp))

            // Lockout banner
            AnimatedVisibility(visible = lockedOut) {
                Box(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(DT.Red.copy(0.15f)).border(1.dp, DT.Red.copy(0.4f), RoundedCornerShape(14.dp))
                        .padding(16.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Lock, null, tint = DT.Red, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.height(6.dp))
                        Text("Account Locked", color = DT.Red, fontWeight = FontWeight.Bold)
                        Text("3 failed attempts", color = DT.SubText, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(6.dp))
                        Text("Try again in ${lockoutSeconds}s", color = DT.Amber, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                }
            }

            if (!lockedOut) {
                // Username
                OutlinedTextField(
                    value = username, onValueChange = { username = it },
                    label = { Text("Username", color = DT.SubText) },
                    leadingIcon = { Icon(Icons.Default.Person, null, tint = DT.Teal) },
                    singleLine = true, shape = RoundedCornerShape(14.dp),
                    colors = fieldColors(), modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))

                // PIN dots
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(DT.Surface).border(1.dp, DT.Border, RoundedCornerShape(14.dp))
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Lock, null, tint = DT.Teal, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(4.dp))
                    repeat(6) { i ->
                        Box(modifier = Modifier.size(14.dp).clip(CircleShape)
                            .background(if (i < pin.length) DT.Teal else DT.Border))
                    }
                    Spacer(Modifier.weight(1f))
                    if (isBiometricAvailable(context)) {
                        IconButton(onClick = { triggerBiometric(context, username, vm) { biometricError = it } },
                            modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Fingerprint, null, tint = DT.Teal, modifier = Modifier.size(26.dp))
                        }
                    }
                }

                if (attempts > 0) {
                    Spacer(Modifier.height(6.dp))
                    Text("${MAX_ATTEMPTS - attempts} attempt${if (MAX_ATTEMPTS - attempts == 1) "" else "s"} remaining",
                        color = DT.Amber, style = MaterialTheme.typography.labelSmall)
                }
                (state.error ?: biometricError)?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, color = DT.Red, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                }

                Spacer(Modifier.height(16.dp))

                // PIN pad
                DarkPinPad(
                    onDigit = { d -> biometricError = null;
                        if (pin.length < 6) {
                            pin += d
                            // Auto-submit handled by LaunchedEffect below
                        }
                    },
                    onDelete = { if (pin.isNotEmpty()) pin = pin.dropLast(1) }
                )

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = { vm.login(username, pin); pin = "" },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    enabled = username.isNotBlank() && pin.length >= 4 && !state.isLoading,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DT.Teal, disabledContainerColor = DT.TealDim)
                ) {
                    if (state.isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                    else Text("Login", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                }
            }
        }
    }
}

private fun isBiometricAvailable(context: Context): Boolean = try {
    BiometricManager.from(context)
        .canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS
} catch (e: Exception) { false }

private fun triggerBiometric(context: Context, username: String, vm: AuthViewModel, onError: (String) -> Unit) {
    try {
        val activity = context as? FragmentActivity ?: return
        val executor = ContextCompat.getMainExecutor(context)
        val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                vm.loginWithBiometric(username)
            }
            override fun onAuthenticationError(code: Int, msg: CharSequence) {
                if (code != BiometricPrompt.ERROR_USER_CANCELED && code != BiometricPrompt.ERROR_NEGATIVE_BUTTON)
                    onError("Biometric: $msg")
            }
            override fun onAuthenticationFailed() { onError("Biometric not recognized") }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("MiniMart POS").setSubtitle("Verify your identity")
            .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL).build()
        prompt.authenticate(info)
    } catch (e: Exception) { /* biometric unavailable — silently ignore */ }
}

@Composable
private fun DarkPinPad(onDigit: (String) -> Unit, onDelete: () -> Unit) {
    val digits = listOf("1","2","3","4","5","6","7","8","9","","0","⌫")
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        digits.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { d ->
                    if (d.isEmpty()) Spacer(Modifier.weight(1f))
                    else Box(
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

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = DT.Teal, unfocusedBorderColor = DT.Border,
    focusedTextColor = DT.OnSurface, unfocusedTextColor = DT.OnSurface,
    cursorColor = DT.Teal, focusedContainerColor = DT.Surface, unfocusedContainerColor = DT.Surface
)
