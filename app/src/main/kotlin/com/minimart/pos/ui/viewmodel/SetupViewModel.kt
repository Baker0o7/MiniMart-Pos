package com.minimart.pos.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minimart.pos.data.repository.ProductRepository
import com.minimart.pos.data.repository.SaleRepository
import com.minimart.pos.data.repository.SettingsRepository
import com.minimart.pos.util.SetupWizard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** First-launch setup: decides whether to show the wizard and saves what the owner chose. */
@HiltViewModel
class SetupViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val products: ProductRepository,
    private val sales: SaleRepository
) : ViewModel() {

    suspend fun shouldShow(isOwner: Boolean): Boolean = try {
        SetupWizard.shouldShow(settings.setupWizardDone.first(), products.getProductCount(), sales.saleCount(), isOwner)
    } catch (_: Exception) { false }

    suspend fun currentStoreName(): String = settings.storeName.first()

    fun finish(storeName: String, currency: String, paperMm: Int, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                if (SetupWizard.validStoreName(storeName)) settings.setStoreName(storeName.trim())
                settings.setCurrency(currency)
                settings.setPaperWidthMm(paperMm)
            } catch (_: Exception) { /* settings can be changed later; never trap the owner in the wizard */ }
            try { settings.setSetupWizardDone() } catch (_: Exception) {}
            onDone()
        }
    }

    fun skip(onDone: () -> Unit) {
        viewModelScope.launch { try { settings.setSetupWizardDone() } catch (_: Exception) {}; onDone() }
    }
}
