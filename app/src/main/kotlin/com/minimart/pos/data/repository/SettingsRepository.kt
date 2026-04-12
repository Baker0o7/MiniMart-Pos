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
        val KEY_PRINTER_ADDRESS  = stringPreferencesKey("printer_address")
        val KEY_PRINTER_NAME     = stringPreferencesKey("printer_name")
        val KEY_RECEIPT_FOOTER   = stringPreferencesKey("receipt_footer")
        val KEY_DARK_MODE        = booleanPreferencesKey("dark_mode")
        val KEY_LOGGED_IN_USER   = longPreferencesKey("logged_in_user_id")
        val KEY_REQUIRE_PIN      = booleanPreferencesKey("require_pin_on_open")
        val KEY_MPESA_PAYBILL    = stringPreferencesKey("mpesa_paybill")
        val KEY_RECEIPT_COUNTER  = intPreferencesKey("receipt_counter")
    }

    val storeName: Flow<String>         = context.dataStore.data.map { it[KEY_STORE_NAME] ?: "My MiniMart" }
    val currency: Flow<String>          = context.dataStore.data.map { it[KEY_CURRENCY] ?: "KES" }
    val taxRate: Flow<Float>            = context.dataStore.data.map { it[KEY_TAX_RATE] ?: 0.16f }
    val printerAddress: Flow<String?>   = context.dataStore.data.map { it[KEY_PRINTER_ADDRESS] }
    val printerName: Flow<String?>      = context.dataStore.data.map { it[KEY_PRINTER_NAME] }
    val receiptFooter: Flow<String>     = context.dataStore.data.map { it[KEY_RECEIPT_FOOTER] ?: "Thank you for shopping with us!" }
    val darkMode: Flow<Boolean>         = context.dataStore.data.map { it[KEY_DARK_MODE] ?: false }
    val loggedInUserId: Flow<Long?>     = context.dataStore.data.map { it[KEY_LOGGED_IN_USER] }
    val requirePin: Flow<Boolean>       = context.dataStore.data.map { it[KEY_REQUIRE_PIN] ?: true }
    val mpesaPaybill: Flow<String>      = context.dataStore.data.map { it[KEY_MPESA_PAYBILL] ?: "" }
    val receiptCounter: Flow<Int>       = context.dataStore.data.map { it[KEY_RECEIPT_COUNTER] ?: 0 }

    suspend fun setStoreName(name: String) = context.dataStore.edit { it[KEY_STORE_NAME] = name }
    suspend fun setCurrency(c: String) = context.dataStore.edit { it[KEY_CURRENCY] = c }
    suspend fun setTaxRate(r: Float) = context.dataStore.edit { it[KEY_TAX_RATE] = r }
    suspend fun setPrinterAddress(addr: String, name: String) = context.dataStore.edit {
        it[KEY_PRINTER_ADDRESS] = addr; it[KEY_PRINTER_NAME] = name
    }
    suspend fun setReceiptFooter(f: String) = context.dataStore.edit { it[KEY_RECEIPT_FOOTER] = f }
    suspend fun setDarkMode(dark: Boolean) = context.dataStore.edit { it[KEY_DARK_MODE] = dark }
    suspend fun setLoggedInUser(userId: Long?) = context.dataStore.edit {
        if (userId == null) it.remove(KEY_LOGGED_IN_USER) else it[KEY_LOGGED_IN_USER] = userId
    }
    suspend fun setMpesaPaybill(pb: String) = context.dataStore.edit { it[KEY_MPESA_PAYBILL] = pb }
    suspend fun incrementReceiptCounter(): Int {
        var newVal = 0
        context.dataStore.edit { prefs ->
            newVal = (prefs[KEY_RECEIPT_COUNTER] ?: 0) + 1
            prefs[KEY_RECEIPT_COUNTER] = newVal
        }
        return newVal
    }
}
