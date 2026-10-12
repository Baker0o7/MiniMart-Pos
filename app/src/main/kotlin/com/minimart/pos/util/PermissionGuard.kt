package com.minimart.pos.util

import com.minimart.pos.data.entity.UserRole
import com.minimart.pos.data.repository.SettingsRepository
import com.minimart.pos.data.repository.UserRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enforces roles below the screens. Screens already hide what a role may not do, but a stale
 * screen or a bug must not be able to void a sale or change a price for someone who can't.
 * A refusal is written to the audit log.
 */
@Singleton
class PermissionGuard @Inject constructor(
    private val settings: SettingsRepository,
    private val users: UserRepository,
    private val audit: AuditLogger
) {
    suspend fun currentRole(): UserRole? {
        val id = settings.loggedInUserId.first() ?: return null
        return users.getUserById(id)?.role
    }

    /** True when the signed-in user's role passes [rule]; otherwise logs the refusal and returns false. */
    suspend fun allow(action: String, rule: (UserRole?) -> Boolean): Boolean {
        val role = currentRole()
        if (rule(role)) return true
        runCatching { audit.log(AuditEvent.PERMISSION_DENIED, detail = "$action refused (${RoleManager.roleLabel(role)})") }
        return false
    }
}

class PermissionDeniedException(action: String) : SecurityException("You don't have permission to $action")
