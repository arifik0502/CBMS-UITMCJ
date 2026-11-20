package com.crispyburger.admin.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crispyburger.admin.data.AdminApi
import com.crispyburger.admin.data.AdminDataBus
import com.crispyburger.admin.data.AdminSalesReport
import com.crispyburger.admin.data.AdminSupabase
import com.crispyburger.admin.data.ReportCsv
import com.crispyburger.admin.data.ReportRange
import com.crispyburger.admin.data.runCatchingCancellable
import java.time.LocalDate
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReportUiState(
    val from: LocalDate = ReportRange.last7Days().first,
    val to: LocalDate = ReportRange.last7Days().second,
    val report: AdminSalesReport? = null,
    val isLoading: Boolean = false,
    val isExporting: Boolean = false,
    val error: String? = null
)

class ReportViewModel(private val api: AdminApi = AdminSupabase) : ViewModel() {
    private val _state = MutableStateFlow(ReportUiState())
    val state: StateFlow<ReportUiState> = _state.asStateFlow()

    private val _csv = MutableSharedFlow<String>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    /** One-shot CSV text for the UI to share. */
    val csv: SharedFlow<String> = _csv.asSharedFlow()

    private var loaded = false
    private var loadJob: Job? = null

    init {
        observeChanges()
    }

    @OptIn(FlowPreview::class)
    private fun observeChanges() {
        viewModelScope.launch {
            AdminDataBus.changes
                .filter {
                    it.origin !== this@ReportViewModel &&
                        (it.kind == AdminDataBus.Kind.ORDERS || it.kind == AdminDataBus.Kind.PAYMENTS)
                }
                .debounce(2_000)
                .collect { if (loaded) generate(silent = true) }
        }
    }

    fun ensureLoaded() {
        if (!loaded) generate()
    }

    fun setRange(from: LocalDate, to: LocalDate) {
        _state.update { it.copy(from = from, to = to) }
        generate()
    }

    fun setFrom(from: LocalDate) = setRange(from, maxOf(_state.value.to, from))

    fun setTo(to: LocalDate) = setRange(minOf(_state.value.from, to), to)

    fun generate(silent: Boolean = false) {
        loaded = true
        val (from, to) = _state.value.let { it.from to it.to }
        val problem = ReportRange.validate(from, to)
        if (problem != null) {
            _state.update { it.copy(error = problem) }
            return
        }
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (!silent) _state.update { it.copy(isLoading = true, error = null) }
            runCatchingCancellable { api.loadSalesReport(from, to) }
                .onSuccess { report -> _state.update { it.copy(report = report, isLoading = false, error = null) } }
                .onFailure { e ->
                    _state.update { it.copy(isLoading = false, error = api.friendlyError(e, "Could not load sales report.")) }
                }
        }
    }

    fun exportCsv() {
        val s = _state.value
        val report = s.report ?: return
        if (s.isExporting) return
        viewModelScope.launch {
            _state.update { it.copy(isExporting = true, error = null) }
            val result = runCatchingCancellable { api.loadAllDeliveredOrders(s.from, s.to) }
            result
                .onSuccess { orders ->
                    _csv.tryEmit(ReportCsv.build(s.from, s.to, report, orders))
                    _state.update { it.copy(isExporting = false) }
                }
                .onFailure { e ->
                    _state.update { it.copy(isExporting = false, error = api.friendlyError(e, "Could not export the report.")) }
                }
        }
    }

    fun reset() {
        loadJob?.cancel()
        loaded = false
        _state.value = ReportUiState()
    }
}
