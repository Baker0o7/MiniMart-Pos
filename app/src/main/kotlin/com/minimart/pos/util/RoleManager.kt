package com.minimart.pos.util

import com.minimart.pos.data.entity.UserRole

/**
 * Role-Based Access Control — single source of truth for what each role can do.
 *
 * OWNER   : full access
 * MANAGER : everything except user management
 * CASHIER : sales only — no prices, no reports, no settings
 */
object RoleManager {

    fun canProcessSales(role: UserRole?)      = true  // everyone
    fun canViewReports(role: UserRole?)       = role == UserRole.OWNER || role == UserRole.MANAGER
    fun canEditPrices(role: UserRole?)        = role == UserRole.OWNER || role == UserRole.MANAGER
    fun canAddProducts(role: UserRole?)       = role == UserRole.OWNER || role == UserRole.MANAGER
    fun canDeleteProducts(role: UserRole?)    = role == UserRole.OWNER
    fun canViewExpenses(role: UserRole?)      = role == UserRole.OWNER || role == UserRole.MANAGER
    fun canAddExpenses(role: UserRole?)       = role == UserRole.OWNER || role == UserRole.MANAGER
    fun canManageUsers(role: UserRole?)       = role == UserRole.OWNER
    fun canManageShifts(role: UserRole?)      = role == UserRole.OWNER || role == UserRole.MANAGER
    fun canAccessSettings(role: UserRole?)    = role == UserRole.OWNER || role == UserRole.MANAGER
    fun canViewInventory(role: UserRole?)     = true  // everyone can see inventory
    fun canAdjustStock(role: UserRole?)       = role == UserRole.OWNER || role == UserRole.MANAGER
    fun canApplyDiscounts(role: UserRole?)    = role == UserRole.OWNER || role == UserRole.MANAGER
    fun canVoidSales(role: UserRole?)         = role == UserRole.OWNER || role == UserRole.MANAGER

    fun roleLabel(role: UserRole?) = when (role) {
        UserRole.OWNER   -> "Owner"
        UserRole.MANAGER -> "Manager"
        UserRole.CASHIER -> "Cashier"
        null             -> "Unknown"
    }

    fun roleBadgeColor(role: UserRole?) = when (role) {
        UserRole.OWNER   -> 0xFF00897B // teal
        UserRole.MANAGER -> 0xFF1565C0 // blue
        UserRole.CASHIER -> 0xFF6A1B9A // purple
        null             -> 0xFF757575
    }
}
