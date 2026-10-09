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
    private val settingsRepo: SettingsRepository,
    private val pinHasher: com.minimart.pos.util.PinHasher,
    private val auditLogger: com.minimart.pos.util.AuditLogger
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

    private fun isOwner() = _state.value.currentUser?.role == UserRole.OWNER

    /** Same rule as the Settings PIN change: at least 4 digits, and not the factory default. */
    private fun pinProblem(pin: String): String? = when {
        pin.length < 4 || pin.length > 12 || !pin.all { it.isDigit() } -> "PIN must be 4–12 digits"
        pin == "1234" -> "Choose a PIN other than 1234"
        else -> null
    }

    fun changePassword(userId: Long, newPin: String) {
        viewModelScope.launch {
            // Enforced here, not only by hiding the screen: only an Owner may reset someone else's PIN.
            if (!isOwner() && _state.value.currentUser?.id != userId) {
                _state.update { it.copy(error = "Only an Owner can change another user's PIN") }
                return@launch
            }
            pinProblem(newPin.trim())?.let { msg -> _state.update { it.copy(error = msg) }; return@launch }
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val user = userRepo.getUserById(userId)
                if (user == null) {
                    // This early return used to leave isLoading stuck on true.
                    _state.update { it.copy(isLoading = false, error = "That user no longer exists") }
                    return@launch
                }
                val hash = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { pinHasher.hash(newPin.trim()) }
                userRepo.updateUser(user.copy(pinHash = hash))
                // Resetting someone's PIN is a sensitive action — it belongs in the audit trail.
                auditLogger.log(com.minimart.pos.util.AuditEvent.PIN_CHANGED,
                    user = _state.value.currentUser?.username ?: "",
                    detail = "PIN changed for ${user.username}")
                _state.update { it.copy(isLoading = false, success = "PIN updated successfully") }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = "Failed: ${e.message}") }
            }
        }
    }

    fun addUser(username: String, displayName: String, pin: String, role: UserRole) {
        viewModelScope.launch {
            if (!isOwner()) { _state.update { it.copy(error = "Only an Owner can add users") }; return@launch }
            pinProblem(pin.trim())?.let { msg -> _state.update { it.copy(error = msg) }; return@launch }
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val cleanName = username.trim()
                if (userRepo.getUserByUsername(cleanName) != null) {
                    _state.update { it.copy(isLoading = false, error = "Username \"$cleanName\" is already taken") }
                    return@launch
                }
                val hash = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { pinHasher.hash(pin.trim()) }
                userRepo.insertUser(User(username = cleanName, pinHash = hash,
                    displayName = displayName.trim(), role = role))
                auditLogger.log(com.minimart.pos.util.AuditEvent.USER_CREATED,
                    user = _state.value.currentUser?.username ?: "", detail = "$cleanName (${role.name})")
                _state.update { it.copy(isLoading = false, success = "User '${displayName.trim()}' added") }
            } catch (e: android.database.sqlite.SQLiteConstraintException) {
                // Removed users keep their row (and username), so the active-only check above can miss it.
                _state.update { it.copy(isLoading = false, error = "Username is already used by a removed account — pick another") }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = "Failed: ${e.message}") }
            }
        }
    }

    fun deactivateUser(user: User) {
        viewModelScope.launch {
            if (!isOwner()) { _state.update { it.copy(error = "Only an Owner can remove users") }; return@launch }
            try {
                // Bug fix: no guard existed against removing the last active Owner.
                // If the only Owner account got deactivated, nobody could reach
                // User Management (Owner-only screen) to re-enable it — permanent lockout.
                if (user.role == UserRole.OWNER) {
                    val activeOwners = _state.value.users.count {
                        it.role == UserRole.OWNER && it.isActive && it.id != user.id
                    }
                    if (activeOwners == 0) {
                        _state.update { it.copy(error = "Cannot remove the last Owner account") }
                        return@launch
                    }
                }
                userRepo.updateUser(user.copy(isActive = false))
                auditLogger.log(com.minimart.pos.util.AuditEvent.USER_DELETED,
                    user = _state.value.currentUser?.username ?: "", detail = "Removed ${user.username}")
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
            GradientHeader(
                title = "User Management",
                onBack = onBack,
                actions = { HeaderPillButton("Add", Icons.Default.PersonAdd) { showAddDialog = true } }
            )
            Spacer(Modifier.height(8.dp))

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
                    item {
                        Box(Modifier.fillMaxWidth()
                            .background(androidx.compose.ui.graphics.Brush.verticalGradient(
                                listOf(DT.Teal, androidx.compose.ui.graphics.Color(0xFF004D40))))
                            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Staff", color = androidx.compose.ui.graphics.Color.White,
                                        fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                                    Text("Manage team access", color = androidx.compose.ui.graphics.Color.White.copy(0.7f), fontSize = 12.sp)
                                }
                                Icon(Icons.Default.People, null,
                                    tint = androidx.compose.ui.graphics.Color.White.copy(0.7f),
                                    modifier = Modifier.size(28.dp))
                            }
                        }
                    }
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
                Text(user.displayName.firstOrNull()?.uppercase() ?: "?", color = DT.Teal, fontWeight = FontWeight.Bold, fontSize = 18.sp)
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
            title = { Text("Remove User?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text("\"${user.displayName}\" will lose access immediately. This cannot be undone.", color = DT.SubText) },
            confirmButton = {
                Button(
                    onClick = { onRemove(); showConfirm = false },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DT.Red, contentColor = Color.White)
                ) {
                    Icon(Icons.Default.PersonRemove, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Remove", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showConfirm = false }, shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DT.Border)) { Text("Cancel", color = DT.SubText) }
            })
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
                        modifier = Modifier.fillMaxWidth().menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable),
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
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DT.Green, contentColor = Color.White,
                    disabledContainerColor = DT.Green.copy(0.45f), disabledContentColor = Color.White.copy(0.7f)
                )
            ) {
                Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add User", fontWeight = FontWeight.ExtraBold)
            }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(12.dp), border = androidx.compose.foundation.BorderStroke(1.dp, DT.Border)) { Text("Cancel", color = DT.SubText) } }
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
                colors = ButtonDefaults.buttonColors(
                containerColor = DT.Green, contentColor = Color.White,
                disabledContainerColor = DT.Green.copy(0.45f), disabledContentColor = Color.White.copy(0.7f)
            )
            ) {
                Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Save", fontWeight = FontWeight.ExtraBold)
            }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(12.dp), border = androidx.compose.foundation.BorderStroke(1.dp, DT.Border)) { Text("Cancel", color = DT.SubText) } }
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
