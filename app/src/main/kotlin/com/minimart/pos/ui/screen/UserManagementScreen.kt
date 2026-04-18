package com.minimart.pos.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minimart.pos.data.entity.User
import com.minimart.pos.data.entity.UserRole
import com.minimart.pos.data.repository.SettingsRepository
import com.minimart.pos.data.repository.UserRepository
import com.minimart.pos.ui.theme.DT
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── ViewModel ────────────────────────────────────────────────────────────────

data class UserMgmtState(
    val users: List<User> = emptyList(),
    val currentUser: User? = null,
    val isLoading: Boolean = false,
    val success: String? = null,
    val error: String? = null
)

@HiltViewModel
class UserManagementViewModel @Inject constructor(
    private val userRepo: UserRepository,
    private val settingsRepo: SettingsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(UserMgmtState())
    val state: StateFlow<UserMgmtState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            userRepo.getAllUsers().catch { emit(emptyList()) }.collect { users ->
                _state.update { it.copy(users = users) }
            }
        }
        viewModelScope.launch {
            settingsRepo.loggedInUserId.collect { uid ->
                if (uid != null) {
                    val user = userRepo.getUserById(uid)
                    _state.update { it.copy(currentUser = user) }
                }
            }
        }
    }

    fun changePassword(userId: Long, newPin: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val user = userRepo.getUserById(userId) ?: return@launch
                val hash = userRepo.sha256(newPin)
                userRepo.updateUser(user.copy(pinHash = hash))
                _state.update { it.copy(isLoading = false, success = "Password updated successfully") }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = "Failed: ${e.message}") }
            }
        }
    }

    fun addUser(username: String, displayName: String, pin: String, role: UserRole) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val hash = userRepo.sha256(pin)
                userRepo.insertUser(User(username = username.trim(), pinHash = hash,
                    displayName = displayName.trim(), role = role))
                _state.update { it.copy(isLoading = false, success = "User '$displayName' added") }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = "Failed: ${e.message}") }
            }
        }
    }

    fun deactivateUser(user: User) {
        viewModelScope.launch {
            try {
                userRepo.updateUser(user.copy(isActive = false))
                _state.update { it.copy(success = "${user.displayName} removed") }
            } catch (e: Exception) {
                _state.update { it.copy(error = "Failed: ${e.message}") }
            }
        }
    }

    fun clearMessages() { _state.update { it.copy(success = null, error = null) } }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserManagementScreen(
    onBack: () -> Unit,
    vm: UserManagementViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var changePinForUser by remember { mutableStateOf<User?>(null) }

    LaunchedEffect(state.success, state.error) {
        if (state.success != null || state.error != null) {
            kotlinx.coroutines.delay(2500); vm.clearMessages()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(DT.Bg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = DT.Teal)
                }
                Text("User Management", color = DT.Teal, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.PersonAdd, null, tint = DT.Teal)
                }
            }

            // Feedback
            state.success?.let { msg ->
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(10.dp)).background(DT.Green.copy(0.15f)).padding(12.dp)) {
                    Icon(Icons.Default.CheckCircle, null, tint = DT.Green, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(msg, color = DT.Green, style = MaterialTheme.typography.bodySmall)
                }
            }
            state.error?.let { err ->
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(10.dp)).background(DT.Red.copy(0.15f)).padding(12.dp)) {
                    Icon(Icons.Default.Error, null, tint = DT.Red, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(err, color = DT.Red, style = MaterialTheme.typography.bodySmall)
                }
            }

            // My account section
            state.currentUser?.let { me ->
                DarkSection("My Account") {
                    DarkUserRow(user = me, isMe = true,
                        onChangePin = { changePinForUser = me },
                        onRemove = null)
                }
                Spacer(Modifier.height(8.dp))
            }

            // All users
            DarkSection("All Users (${state.users.size})") {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.users.filter { it.id != state.currentUser?.id }, key = { it.id }) { user ->
                        DarkUserRow(
                            user = user,
                            isMe = false,
                            onChangePin = { changePinForUser = user },
                            onRemove = { vm.deactivateUser(user) }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddUserDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { u, d, p, r -> vm.addUser(u, d, p, r); showAddDialog = false }
        )
    }

    changePinForUser?.let { user ->
        ChangePinDialog(
            userName = user.displayName,
            onDismiss = { changePinForUser = null },
            onSave = { newPin -> vm.changePassword(user.id, newPin); changePinForUser = null }
        )
    }
}

@Composable
private fun DarkSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(title, color = DT.SubText, style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(bottom = 8.dp))
        content()
    }
}

@Composable
private fun DarkUserRow(user: User, isMe: Boolean, onChangePin: () -> Unit, onRemove: (() -> Unit)?) {
    var showConfirm by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(DT.Surface).border(1.dp, DT.Border, RoundedCornerShape(14.dp)).padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Avatar
            Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(DT.TealDim),
                contentAlignment = Alignment.Center) {
                Text(user.displayName.first().uppercase(), color = DT.Teal, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(user.displayName, color = DT.OnSurface, fontWeight = FontWeight.SemiBold)
                    if (isMe) {
                        Spacer(Modifier.width(6.dp))
                        Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(DT.Teal.copy(0.2f)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                            Text("You", color = DT.Teal, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Text("@${user.username} • ${user.role.name}", color = DT.SubText, style = MaterialTheme.typography.labelSmall)
            }
            // Change PIN
            IconButton(onClick = onChangePin, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Lock, null, tint = DT.TealLight, modifier = Modifier.size(18.dp))
            }
            // Remove (not for self)
            if (onRemove != null) {
                IconButton(onClick = { showConfirm = true }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Delete, null, tint = DT.Red, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
    if (showConfirm && onRemove != null) {
        AlertDialog(onDismissRequest = { showConfirm = false }, containerColor = DT.Surface,
            title = { Text("Remove User?", color = DT.OnSurface) },
            text = { Text("Remove ${user.displayName} from the system?", color = DT.SubText) },
            confirmButton = { TextButton(onClick = { onRemove(); showConfirm = false }) { Text("Remove", color = DT.Red) } },
            dismissButton = { TextButton(onClick = { showConfirm = false }) { Text("Cancel", color = DT.SubText) } })
    }
}

// ─── Add User Dialog ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddUserDialog(onDismiss: () -> Unit, onAdd: (String, String, String, UserRole) -> Unit) {
    var username    by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var pin         by remember { mutableStateOf("") }
    var confirmPin  by remember { mutableStateOf("") }
    var role        by remember { mutableStateOf(UserRole.CASHIER) }
    var expanded    by remember { mutableStateOf(false) }
    val pinsMatch = pin == confirmPin && pin.length >= 4

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DT.Surface,
        title = { Text("Add New User", color = DT.OnSurface, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DarkInput(displayName, { displayName = it }, "Full Name *")
                DarkInput(username, { username = it }, "Username *")
                DarkInput(pin, { if (it.length <= 6 && it.all(Char::isDigit)) pin = it }, "PIN (4-6 digits) *",
                    keyboardType = KeyboardType.NumberPassword, isPassword = true)
                DarkInput(confirmPin, { if (it.length <= 6 && it.all(Char::isDigit)) confirmPin = it }, "Confirm PIN *",
                    keyboardType = KeyboardType.NumberPassword, isPassword = true)
                if (pin.isNotEmpty() && confirmPin.isNotEmpty() && !pinsMatch) {
                    Text("PINs don't match", color = DT.Red, style = MaterialTheme.typography.labelSmall)
                }
                // Role picker
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    OutlinedTextField(
                        value = role.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Role", color = DT.SubText) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        colors = darkTextFieldColors()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false },
                        containerColor = DT.Surface2) {
                        UserRole.entries.forEach { r ->
                            DropdownMenuItem(text = { Text(r.name, color = DT.OnSurface) },
                                onClick = { role = r; expanded = false })
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(username, displayName, pin, role) },
                enabled = username.isNotBlank() && displayName.isNotBlank() && pinsMatch,
                colors = ButtonDefaults.buttonColors(containerColor = DT.Teal)
            ) { Text("Add User") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = DT.SubText) } }
    )
}

// ─── Change PIN Dialog ────────────────────────────────────────────────────────

@Composable
private fun ChangePinDialog(userName: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var newPin     by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    val pinsMatch = newPin == confirmPin && newPin.length >= 4

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DT.Surface,
        title = { Text("Change PIN — $userName", color = DT.OnSurface, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DarkInput(newPin, { if (it.length <= 6 && it.all(Char::isDigit)) newPin = it },
                    "New PIN (4-6 digits)", keyboardType = KeyboardType.NumberPassword, isPassword = true)
                DarkInput(confirmPin, { if (it.length <= 6 && it.all(Char::isDigit)) confirmPin = it },
                    "Confirm PIN", keyboardType = KeyboardType.NumberPassword, isPassword = true)
                if (newPin.isNotEmpty() && confirmPin.isNotEmpty() && !pinsMatch) {
                    Text("PINs don't match", color = DT.Red, style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(newPin) },
                enabled = pinsMatch,
                colors = ButtonDefaults.buttonColors(containerColor = DT.Teal)
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = DT.SubText) } }
    )
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

@Composable
private fun DarkInput(value: String, onValueChange: (String) -> Unit, label: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    keyboardType: KeyboardType = KeyboardType.Text, isPassword: Boolean = false) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange,
        label = { Text(label, color = DT.SubText, style = MaterialTheme.typography.labelSmall) },
        singleLine = true, modifier = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        colors = darkTextFieldColors()
    )
}

@Composable
private fun darkTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = DT.Teal, unfocusedBorderColor = DT.Border,
    focusedTextColor = DT.OnSurface, unfocusedTextColor = DT.OnSurface,
    cursorColor = DT.Teal, focusedContainerColor = DT.Bg, unfocusedContainerColor = DT.Bg
)
