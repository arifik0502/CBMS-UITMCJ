package com.crispyburger.admin.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crispyburger.admin.data.AdminApi
import com.crispyburger.admin.data.AdminDataBus
import com.crispyburger.admin.data.AdminInventoryItem
import com.crispyburger.admin.data.AdminSupabase
import com.crispyburger.admin.data.InventoryRules
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

data class AdjustDraft(
    val item: AdminInventoryItem,
    val type: String,
    val quantityText: String = "",
    val reasonText: String = ""
) {
    val quantity: Int? get() = quantityText.toIntOrNull()
    val resultingPhysical: Int? get() = InventoryRules.resultingPhysical(type, item.stock.physicalStock, quantity)
    val meetsReservation: Boolean get() = InventoryRules.meetsReservation(resultingPhysical, item.stock.reservedStock)
    val canSave: Boolean
        get() = InventoryRules.isValidQuantity(type, quantity) && meetsReservation && reasonText.trim().length >= 3
}

data class InventoryUiState(
    val items: List<AdminInventoryItem> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val success: String? = null,
    val draft: AdjustDraft? = null
)

class InventoryViewModel(private val api: AdminApi = AdminSupabase) : ViewModel() {
    private val _state = MutableStateFlow(InventoryUiState())
    val state: StateFlow<InventoryUiState> = _state.asStateFlow()

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
                    it.origin !== this@InventoryViewModel && it.kind != AdminDataBus.Kind.PAYMENTS
                }
                .debounce(1_500)
                .collect { if (loaded) refresh(silent = true) }
        }
    }

    fun ensureLoaded() {
        if (!loaded) refresh()
    }

    fun refresh(silent: Boolean = false) {
        loaded = true
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            if (!silent) _state.update { it.copy(isLoading = true, error = null) }
            runCatchingCancellable { api.loadInventory() }
                .onSuccess { items -> _state.update { it.copy(items = items, isLoading = false, error = null) } }
                .onFailure { e ->
                    _state.update { it.copy(isLoading = false, error = api.friendlyError(e, "Could not load inventory.")) }
                }
        }
    }

    fun startAdjust(item: AdminInventoryItem, type: String) =
        _state.update { it.copy(draft = AdjustDraft(item, type), error = null, success = null) }

    fun updateQuantity(value: String) =
        _state.update { s -> s.copy(draft = s.draft?.copy(quantityText = value.filter { it.isDigit() }.take(6))) }

    fun updateReason(value: String) =
        _state.update { s -> s.copy(draft = s.draft?.copy(reasonText = value)) }

    fun dismissDraft() = _state.update { it.copy(draft = null) }

    fun save() {
        val draft = _state.value.draft ?: return
        if (!draft.canSave || _state.value.isSaving) return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null, success = null) }
            val result = runCatchingCancellable {
                api.adjustInventory(draft.item.product.id, draft.type, draft.quantity ?: 0, draft.reasonText.trim())
            }
            if (result.isFailure) {
                _state.update {
                    it.copy(
                        isSaving = false,
                        error = api.friendlyError(
                            result.exceptionOrNull() ?: IllegalStateException(),
                            "Inventory adjustment failed. Check your connection and inventory permissions."
                        )
                    )
                }
                return@launch
            }
            _state.update { it.copy(isSaving = false, draft = null, success = "Inventory updated and logged.") }
            AdminDataBus.emit(AdminDataBus.Kind.INVENTORY, origin = this@InventoryViewModel)
            loadJob?.cancel()
            loadJob = null
            refresh(silent = true)
        }
    }

    fun reset() {
        loadJob?.cancel()
        loaded = false
        _state.value = InventoryUiState()
    }
}
