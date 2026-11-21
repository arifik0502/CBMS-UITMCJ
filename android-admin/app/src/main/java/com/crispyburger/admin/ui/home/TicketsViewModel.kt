package com.crispyburger.admin.ui.home

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crispyburger.admin.data.AdminApi
import com.crispyburger.admin.data.AdminDataBus
import com.crispyburger.admin.data.AdminSupabase
import com.crispyburger.admin.data.AdminTicket
import com.crispyburger.admin.data.ReceiptLoader
import com.crispyburger.admin.data.TicketFilter
import com.crispyburger.admin.data.TicketRules
import com.crispyburger.admin.data.runCatchingCancellable
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TicketsUiState(
    val tickets: List<AdminTicket> = emptyList(),
    val filter: TicketFilter = TicketFilter.OPEN,
    val isLoading: Boolean = false,
    val isWorking: Boolean = false,
    val error: String? = null,
    val info: String? = null,
    val selectedId: String? = null,
    val photo: ReceiptState = ReceiptState.None
) {
    val selected: AdminTicket? get() = selectedId?.let { id -> tickets.firstOrNull { it.id == id } }
    val openCount: Int get() = TicketRules.openCount(tickets)
    val visible: List<AdminTicket> get() = TicketRules.filter(tickets, filter)
}

class TicketsViewModel(
    private val api: AdminApi = AdminSupabase,
    private val photoDownloader: suspend (String) -> Bitmap? = { ReceiptLoader.download(it) }
) : ViewModel() {

    private val _state = MutableStateFlow(TicketsUiState())
    val state: StateFlow<TicketsUiState> = _state.asStateFlow()

    private var loaded = false
    private var loadJob: Job? = null
    private var reloadQueued = false

    init {
        observeChanges()
    }

    @OptIn(FlowPreview::class)
    private fun observeChanges() {
        viewModelScope.launch {
            AdminDataBus.changes
                .filter { it.origin !== this@TicketsViewModel && it.kind == AdminDataBus.Kind.TICKETS }
                .debounce(600)
                .collect { if (loaded) refresh(silent = true) }
        }
    }

    fun ensureLoaded() {
        if (!loaded) refresh()
    }

    fun refresh(silent: Boolean = false) {
        loaded = true
        if (loadJob?.isActive == true) {
            reloadQueued = true
            return
        }
        loadJob = viewModelScope.launch {
            do {
                reloadQueued = false
                if (!silent) _state.update { it.copy(isLoading = true, error = null) }
                runCatchingCancellable { api.loadTickets() }
                    .onSuccess { list -> _state.update { it.copy(tickets = list, isLoading = false, error = null) } }
                    .onFailure { e ->
                        _state.update {
                            it.copy(
                                isLoading = false,
                                error = api.friendlyError(
                                    e,
                                    "Could not load tickets. Check that the support tickets migration has been applied."
                                )
                            )
                        }
                    }
            } while (reloadQueued)
        }
    }

    fun setFilter(filter: TicketFilter) = _state.update { it.copy(filter = filter) }

    fun select(ticketId: String) =
        _state.update { it.copy(selectedId = ticketId, photo = ReceiptState.None, error = null) }

    fun dismissDetail() = _state.update { it.copy(selectedId = null, photo = ReceiptState.None) }

    fun clearMessages() = _state.update { it.copy(info = null, error = null) }

    /** Moves the selected ticket to the other status (Open <-> Resolved). */
    fun toggleStatus() {
        val ticket = _state.value.selected ?: return
        if (_state.value.isWorking) return
        val target = TicketRules.toggled(ticket.status)
        viewModelScope.launch {
            _state.update { it.copy(isWorking = true, error = null, info = null) }
            val result = runCatchingCancellable { api.setTicketStatus(ticket.id, target) }
            if (result.isFailure) {
                _state.update {
                    it.copy(
                        isWorking = false,
                        error = api.friendlyError(
                            result.exceptionOrNull() ?: IllegalStateException(),
                            "Could not update the ticket. Check your connection and permissions."
                        )
                    )
                }
                return@launch
            }
            _state.update {
                it.copy(
                    isWorking = false,
                    info = "${ticket.ticketNo} marked ${target.lowercase()}.",
                    selectedId = null,
                    photo = ReceiptState.None
                )
            }
            AdminDataBus.emit(AdminDataBus.Kind.TICKETS, origin = this@TicketsViewModel)
            refresh(silent = true)
        }
    }

    fun viewPhoto() {
        val path = _state.value.selected?.attachmentPath ?: return
        viewModelScope.launch {
            _state.update { it.copy(photo = ReceiptState.Loading, error = null) }
            val urlResult = runCatchingCancellable { api.createTicketPhotoUrl(path) }
            val url = urlResult.getOrNull()
            if (url == null) {
                _state.update {
                    it.copy(
                        photo = ReceiptState.None,
                        error = urlResult.exceptionOrNull()?.let { e -> api.friendlyError(e, "Could not open the photo.") }
                    )
                }
                return@launch
            }
            val bitmap = photoDownloader(url)
            _state.update {
                it.copy(photo = if (bitmap != null) ReceiptState.Ready(bitmap, url) else ReceiptState.Failed(url))
            }
        }
    }

    fun dismissPhoto() = _state.update { it.copy(photo = ReceiptState.None) }

    fun reset() {
        loadJob?.cancel()
        loaded = false
        reloadQueued = false
        _state.value = TicketsUiState()
    }
}
