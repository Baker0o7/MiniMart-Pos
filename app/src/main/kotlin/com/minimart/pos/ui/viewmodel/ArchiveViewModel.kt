package com.minimart.pos.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minimart.pos.data.repository.DatabaseArchiver
import com.minimart.pos.util.ArchivePlan
import com.minimart.pos.util.ArchivePolicy
import com.minimart.pos.util.ErrorAdvice
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class ArchiveUiState(
    val months: Int = ArchivePolicy.DEFAULT_MONTHS,
    val plan: ArchivePlan? = null,
    val working: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val lastFile: File? = null
)

@HiltViewModel
class ArchiveViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val archiver: DatabaseArchiver
) : ViewModel() {
    private val _state = MutableStateFlow(ArchiveUiState())
    val state: StateFlow<ArchiveUiState> = _state.asStateFlow()

    init { check() }

    fun setMonths(months: Int) { _state.update { it.copy(months = ArchivePolicy.clampMonths(months), message = null, error = null) }; check() }

    fun check() {
        viewModelScope.launch {
            val months = _state.value.months
            try {
                val plan = withContext(Dispatchers.IO) { archiver.plan(months) }
                _state.update { it.copy(plan = plan) }
            } catch (e: Exception) { _state.update { it.copy(error = ErrorAdvice.explain(e, "Checking old sales")) } }
        }
    }

    fun archive() {
        if (_state.value.working) return
        _state.update { it.copy(working = true, message = null, error = null) }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            try {
                val result = withContext(Dispatchers.IO) {
                    archiver.archive(_state.value.months, now, File(directory(context), ArchivePolicy.fileName(now)))
                }
                _state.update { it.copy(working = false, lastFile = result.file,
                    message = "Archived ${result.sales} sale(s) to ${result.file.name} (${result.bytes / 1024} KB). Share or copy that file somewhere safe: it is now the only copy of those sales.") }
            } catch (e: com.minimart.pos.data.repository.ArchiveException) {
                _state.update { it.copy(working = false, error = e.message) }
            } catch (e: Exception) {
                _state.update { it.copy(working = false, error = ErrorAdvice.explain(e, "Archiving")) }
            }
            check()
        }
    }

    companion object {
        /** Private app storage (never the external folder, which other apps can read on older Android); shared through the file provider. */
        fun directory(context: Context): File = File(context.filesDir, "archives")
    }
}
