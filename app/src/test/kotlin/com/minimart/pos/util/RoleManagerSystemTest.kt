package com.minimart.pos.util

import com.minimart.pos.data.entity.UserRole
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoleManagerSystemTest {
    @Test fun onlyTheOwnerManagesPaymentCredentialsSyncAndBackups() {
        assertTrue(RoleManager.canManageSystem(UserRole.OWNER))
        assertFalse(RoleManager.canManageSystem(UserRole.MANAGER))
        assertFalse(RoleManager.canManageSystem(UserRole.CASHIER))
        assertFalse(RoleManager.canManageSystem(null))
    }

    @Test fun managersStillReachOrdinarySettings() = assertTrue(RoleManager.canAccessSettings(UserRole.MANAGER))
}
