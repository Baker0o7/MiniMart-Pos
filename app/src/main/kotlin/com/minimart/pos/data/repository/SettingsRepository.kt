package com.minimart.pos.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "minimart_settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        val KEY_STORE_NAME       = stringPreferencesKey("store_name")
        val KEY_CURRENCY         = stringPreferencesKey("currency")
        val KEY_TAX_RATE         = floatPreferencesKey("tax_rate")
        val KEY_HIDDEN_ACTIONS    = stringPreferencesKey("hidden_actions")
        val KEY_EXPIRY_ALERT_MONTHS = intPreferencesKey("expiry_alert_months")
        val KEY_CASH_DRAWER_ADDRESS = stringPreferencesKey("cash_drawer_address")
        val KEY_CASH_DRAWER_ON_SALE = booleanPreferencesKey("cash_drawer_on_sale")
        val KEY_PRINTER_ADDRESS     = stringPreferencesKey("printer_address")
        val KEY_PRINTER_NAME     = stringPreferencesKey("printer_name")
        val KEY_RECEIPT_FOOTER   = stringPreferencesKey("receipt_footer")
        val KEY_DARK_MODE        = booleanPreferencesKey("dark_mode")
        val KEY_LOGGED_IN_USER   = longPreferencesKey("logged_in_user_id")
        val KEY_REQUIRE_PIN      = booleanPreferencesKey("require_pin_on_open")
        val KEY_MPESA_PAYBILL    = stringPreferencesKey("mpesa_paybill")
        val KEY_MPESA_TILL       = stringPreferencesKey("mpesa_till")
        val KEY_MPESA_WITHDRAW   = stringPreferencesKey("mpesa_withdraw_number")
        val KEY_MPESA_NAME       = stringPreferencesKey("mpesa_account_name")
        val KEY_RECEIPT_COUNTER  = intPreferencesKey("receipt_counter")
        // Bug fix: PIN-attempt lockout used to live only in LoginScreen's `remember {}`
        // state, which is wiped on process death — force-closing the app (or the OS
        // killing it under memory pressure, common on budget devices) instantly reset
        // the lockout, making the "3 failed attempts" security feature trivially
        // bypassable. Now persisted via DataStore so it survives app restarts.
        val KEY_FAILED_ATTEMPTS  = intPreferencesKey("failed_pin_attempts")
        val KEY_LOCKOUT_UNTIL    = longPreferencesKey("lockout_until_epoch_ms")
    }

    val storeName: Flow<String>         = context.dataStore.data.map { it[KEY_STORE_NAME] ?: "My MiniMart" }
    val currency: Flow<String>          = context.dataStore.data.map { it[KEY_CURRENCY] ?: "KES" }
    val taxRate: Flow<Float>            = context.dataStore.data.map { it[KEY_TAX_RATE] ?: 0.16f }
    // expiry alert threshold in months (1, 2, or 3)
    val expiryAlertMonths: Flow<Int> = context.dataStore.data.map { it[KEY_EXPIRY_ALERT_MONTHS] ?: 1 }
    suspend fun setExpiryAlertMonths(months: Int) = context.dataStore.edit { it[KEY_EXPIRY_ALERT_MONTHS] = months }

    // comma-separated list of hidden action ids
    val hiddenActions: Flow<String> = context.dataStore.data.map { it[KEY_HIDDEN_ACTIONS] ?: "" }
    suspend fun setHiddenActions(ids: String) = context.dataStore.edit { it[KEY_HIDDEN_ACTIONS] = ids }

    val printerAddress: Flow<String?>   = context.dataStore.data.map { it[KEY_PRINTER_ADDRESS] }
    val printerName: Flow<String?>      = context.dataStore.data.map { it[KEY_PRINTER_NAME] }
    val receiptFooter: Flow<String>     = context.dataStore.data.map { it[KEY_RECEIPT_FOOTER] ?: "Thank you for shopping with us!" }
    val darkMode: Flow<Boolean>         = context.dataStore.data.map { it[KEY_DARK_MODE] ?: false }
    val loggedInUserId: Flow<Long?>     = context.dataStore.data.map { it[KEY_LOGGED_IN_USER] }
    val requirePin: Flow<Boolean>       = context.dataStore.data.map { it[KEY_REQUIRE_PIN] ?: true }
    val mpesaPaybill: Flow<String>      = context.dataStore.data.map { it[KEY_MPESA_PAYBILL] ?: "" }
    val mpesaTill: Flow<String>         = context.dataStore.data.map { it[KEY_MPESA_TILL] ?: "" }
    val mpesaWithdraw: Flow<String>     = context.dataStore.data.map { it[KEY_MPESA_WITHDRAW] ?: "" }
    val mpesaAccountName: Flow<String>  = context.dataStore.data.map { it[KEY_MPESA_NAME] ?: "" }
    val receiptCounter: Flow<Int>       = context.dataStore.data.map { it[KEY_RECEIPT_COUNTER] ?: 0 }

    suspend fun setStoreName(name: String) = context.dataStore.edit { it[KEY_STORE_NAME] = name }
    suspend fun setCurrency(c: String) = context.dataStore.edit { it[KEY_CURRENCY] = c }
    suspend fun setTaxRate(r: Float) = context.dataStore.edit { it[KEY_TAX_RATE] = r }
    suspend fun setPrinterAddress(addr: String, name: String) = context.dataStore.edit {
        it[KEY_PRINTER_ADDRESS] = addr; it[KEY_PRINTER_NAME] = name
    }

    val cashDrawerAddress: Flow<String> = context.dataStore.data.map { it[KEY_CASH_DRAWER_ADDRESS] ?: "" }
    val cashDrawerOnSale:  Flow<Boolean> = context.dataStore.data.map { it[KEY_CASH_DRAWER_ON_SALE] ?: true }
    suspend fun setCashDrawerAddress(addr: String) = context.dataStore.edit { it[KEY_CASH_DRAWER_ADDRESS] = addr }
    suspend fun setCashDrawerOnSale(v: Boolean) = context.dataStore.edit { it[KEY_CASH_DRAWER_ON_SALE] = v }
    suspend fun setReceiptFooter(f: String) = context.dataStore.edit { it[KEY_RECEIPT_FOOTER] = f }
    suspend fun setDarkMode(dark: Boolean) = context.dataStore.edit { it[KEY_DARK_MODE] = dark }
    suspend fun setLoggedInUser(userId: Long?) = context.dataStore.edit {
        if (userId == null) it.remove(KEY_LOGGED_IN_USER) else it[KEY_LOGGED_IN_USER] = userId
    }
    suspend fun setMpesaPaybill(pb: String)    = context.dataStore.edit { it[KEY_MPESA_PAYBILL] = pb }
    suspend fun setMpesaTill(t: String)        = context.dataStore.edit { it[KEY_MPESA_TILL] = t }
    suspend fun setMpesaWithdraw(n: String)    = context.dataStore.edit { it[KEY_MPESA_WITHDRAW] = n }
    suspend fun setMpesaAccountName(n: String) = context.dataStore.edit { it[KEY_MPESA_NAME] = n }
    suspend fun incrementReceiptCounter(): Int {
        var newVal = 0
        context.dataStore.edit { prefs ->
            newVal = (prefs[KEY_RECEIPT_COUNTER] ?: 0) + 1
            prefs[KEY_RECEIPT_COUNTER] = newVal
        }
        return newVal
    }

    // ── PIN lockout (persisted — survives process death / force-close) ─────────
    val failedAttempts: Flow<Int>     = context.dataStore.data.map { it[KEY_FAILED_ATTEMPTS] ?: 0 }
    val lockoutUntilMs: Flow<Long>    = context.dataStore.data.map { it[KEY_LOCKOUT_UNTIL] ?: 0L }

    suspend fun recordFailedAttempt(maxAttempts: Int, lockoutDurationMs: Long): Int {
        var newCount = 0
        context.dataStore.edit { prefs ->
            newCount = (prefs[KEY_FAILED_ATTEMPTS] ?: 0) + 1
            prefs[KEY_FAILED_ATTEMPTS] = newCount
            if (newCount >= maxAttempts) {
                prefs[KEY_LOCKOUT_UNTIL] = System.currentTimeMillis() + lockoutDurationMs
            }
        }
        return newCount
    }

    suspend fun clearLockout() {
        context.dataStore.edit { prefs ->
            prefs[KEY_FAILED_ATTEMPTS] = 0
            prefs[KEY_LOCKOUT_UNTIL] = 0L
        }
    }
}
