package com.minimart.pos.ui.viewmodel

import android.content.Context
import android.content.SharedPreferences
import android.os.StatFs
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minimart.pos.data.dao.SyncDao
import com.minimart.pos.data.repository.IntegrityChecker
import com.minimart.pos.data.repository.MpesaPaymentRepository
import com.minimart.pos.data.repository.ProductRepository
import com.minimart.pos.data.repository.SaleRepository
import com.minimart.pos.data.repository.SettingsRepository
import com.minimart.pos.data.repository.ShiftRepository
import com.minimart.pos.data.repository.UserRepository
import com.minimart.pos.util.BackupManager
import com.minimart.pos.util.CrashRecorder
import com.minimart.pos.util.ErrorAdvice
import com.minimart.pos.util.HealthCheck
import com.minimart.pos.util.HealthInputs
import com.minimart.pos.util.HealthLevel
import com.minimart.pos.util.PinHasher
import com.minimart.pos.util.StoreHealth
import com.minimart.pos.util.todayStartMs
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class StoreHealthState(
    val loading: Boolean = true,
    val checks: List<HealthCheck> = emptyList(),
    val overall: HealthLevel = HealthLevel.GOOD,
    val headline: String = "",
    val checkedAt: Long = 0L,
    /** What the data check found, shown under the "Sales and stock records" row. */
    val dataProblems: List<String> = emptyList(),
    val error: String? = null
)

/** Gathers the facts about this shop's data and phone, then lets [StoreHealth] judge them. */
@HiltViewModel
class StoreHealthViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val checker: IntegrityChecker,
    private val syncDao: SyncDao,
    private val settings: SettingsRepository,
    private val users: UserRepository,
    private val sales: SaleRepository,
    private val mpesa: MpesaPaymentRepository,
    private val products: ProductRepository,
    private val shifts: ShiftRepository,
    private val pinHasher: PinHasher,
    private val prefs: SharedPreferences
) : ViewModel() {
    private val _state = MutableStateFlow(StoreHealthState())
    val state: StateFlow<StoreHealthState> = _state.asStateFlow()
    private var running = false

    init { refresh() }

    fun refresh() {
        if (running) return
        running = true
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val (inputs, problems) = withContext(Dispatchers.IO) { gather(now) }
                val checks = StoreHealth.evaluate(inputs)
                _state.value = StoreHealthState(
                    loading = false, checks = checks, overall = StoreHealth.overall(checks),
                    headline = StoreHealth.headline(checks), checkedAt = now, dataProblems = problems
                )
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = ErrorAdvice.explain(e, "The health check")) }
            } finally { running = false }
        }
    }

    private suspend fun gather(now: Long): Pair<HealthInputs, List<String>> {
        val report = try { checker.check() } catch (_: Exception) { null }
        val (lastOk, _, _) = BackupManager.healthSnapshot(context)
        val newestFile = BackupManager.listBackups(context).maxOfOrNull { it.lastModified() } ?: 0L
        val lastBackup = maxOf(lastOk, newestFile)
        val dayMs = 24L * 60 * 60 * 1000

        val peerSet = !prefs.getString("peer_ip", "").isNullOrBlank()
        val serverOn = runCatching { settings.syncServerEnabled.first() }.getOrDefault(false)
        val configured = peerSet || serverOn
        val weak = configured && runCatching { !com.minimart.pos.util.PairingCode.isStrong(settings.getOrCreateSyncSecret()) }.getOrDefault(false)

        val reconciliation = runCatching { reconcileRange(mpesa, sales, todayStartMs(), Long.MAX_VALUE) }.getOrNull()
        val defaultPin = runCatching { users.getAllUsers().first().any { pinHasher.verify("1234", it.pinHash) } }.getOrDefault(false)
        val expired = runCatching { products.getExpiryCandidates().first().count { it.expiryDate in 1 until now } }.getOrDefault(0)
        val low = runCatching { products.getLowStockProducts().first().size }.getOrDefault(0)
        val stale = runCatching { shifts.openShifts().count { now - it.clockIn > 16L * 60 * 60 * 1000 } }.getOrDefault(0)
        val freeMb = runCatching { StatFs(context.filesDir.path).availableBytes / (1024 * 1024) }.getOrNull()

        val inputs = HealthInputs(
            dataProblems = report?.issues?.size,
            lastBackupAgeDays = if (lastBackup > 0) ((now - lastBackup) / dayMs).toInt() else null,
            backupEverMade = lastBackup > 0,
            pendingSync = runCatching { syncDao.countPending() }.getOrDefault(0),
            refusedSync = runCatching { syncDao.getConflictCount().first() }.getOrDefault(0),
            syncConfigured = configured,
            freeStorageMb = freeMb,
            unclaimedMpesaToday = reconciliation?.unclaimedPayments?.size ?: 0,
            lowStock = low,
            expired = expired,
            crashReports = CrashRecorder.recentCount(context),
            secureStorageAvailable = settings.secureStorageAvailable,
            weakPairingCode = weak,
            defaultPinStillUsed = defaultPin,
            staleOpenShifts = stale
        )
        return inputs to (report?.issues.orEmpty().take(10))
    }
}
