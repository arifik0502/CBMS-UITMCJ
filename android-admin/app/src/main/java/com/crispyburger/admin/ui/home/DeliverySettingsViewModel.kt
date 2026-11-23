package com.crispyburger.admin.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crispyburger.admin.data.AdminApi
import com.crispyburger.admin.data.AdminBuilding
import com.crispyburger.admin.data.AdminDeliverySettings
import com.crispyburger.admin.data.AdminDeliveryTrip
import com.crispyburger.admin.data.AdminSupabase
import com.crispyburger.admin.data.runCatchingCancellable
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TripDraft(
    val id: String? = null,
    val tripNo: String = "",
    val label: String = "",
    val startTime: String = "19:00:00",
    val endTime: String = "19:30:00",
    val isOpen: Boolean = true
) {
    val isValid: Boolean
        get() = tripNo.toIntOrNull() in 1..50 &&
            label.trim().isNotEmpty() &&
            startTime.matches(Regex("^([01]\\d|2[0-3]):[0-5]\\d:00$")) &&
            endTime.matches(Regex("^([01]\\d|2[0-3]):[0-5]\\d:00$")) &&
            endTime != startTime

    companion object {
        fun from(trip: AdminDeliveryTrip) = TripDraft(
            id = trip.id,
            tripNo = trip.tripNo.toString(),
            label = trip.label,
            startTime = trip.startTime,
            endTime = trip.endTime,
            isOpen = trip.isOpen
        )
    }
}

data class BuildingDraft(
    val id: String? = null,
    val code: String = "",
    val name: String = "",
    val floorsText: String = "",
    val sortOrder: String = "0",
    val isActive: Boolean = true
) {
    val floors: List<Int>?
        get() {
            val parts = floorsText.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            if (parts.isEmpty()) return null
            val values = parts.map { it.toIntOrNull() ?: return null }
            if (values.any { it !in 0..100 } || values.distinct().size != values.size) return null
            return values.sorted()
        }

    val isValid: Boolean
        get() = code.trim().uppercase().matches(Regex("^[A-Z0-9][A-Z0-9_-]{0,7}$")) &&
            name.trim().isNotEmpty() &&
            floors != null &&
            sortOrder.toIntOrNull() in 0..1000

    companion object {
        fun from(building: AdminBuilding) = BuildingDraft(
            id = building.id,
            code = building.code,
            name = building.name,
            floorsText = building.floors.joinToString(", "),
            sortOrder = building.sortOrder.toString(),
            isActive = building.isActive
        )
    }
}

data class DeliverySettingsUiState(
    val settings: AdminDeliverySettings? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val success: String? = null
)

class DeliverySettingsViewModel(private val api: AdminApi = AdminSupabase) : ViewModel() {
    private val _state = MutableStateFlow(DeliverySettingsUiState())
    val state: StateFlow<DeliverySettingsUiState> = _state.asStateFlow()

    private var loaded = false
    private var loadJob: Job? = null

    fun ensureLoaded() {
        if (!loaded) refresh()
    }

    fun refresh() {
        loaded = true
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            runCatchingCancellable { api.loadDeliverySettings() }
                .onSuccess { settings ->
                    _state.update {
                        it.copy(settings = settings, isLoading = false, error = null)
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = api.friendlyError(e, "Could not load website delivery settings.")
                        )
                    }
                }
        }
    }

    fun saveTrip(draft: TripDraft, onDone: () -> Unit = {}) {
        if (!draft.isValid || _state.value.isSaving) return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null, success = null) }
            val result = runCatchingCancellable {
                api.saveDeliveryTrip(
                    id = draft.id,
                    tripNo = requireNotNull(draft.tripNo.toIntOrNull()),
                    label = draft.label,
                    startTime = draft.startTime,
                    endTime = draft.endTime,
                    isOpen = draft.isOpen
                )
            }
            if (result.isFailure) {
                _state.update {
                    it.copy(
                        isSaving = false,
                        error = api.friendlyError(result.exceptionOrNull() ?: IllegalStateException(), "Could not save the delivery trip.")
                    )
                }
                return@launch
            }
            _state.update { it.copy(isSaving = false, success = "Delivery trip saved.") }
            loadJob = null
            refresh()
            onDone()
        }
    }

    fun saveBuilding(draft: BuildingDraft, onDone: () -> Unit = {}) {
        val floors = draft.floors ?: return
        if (!draft.isValid || _state.value.isSaving) return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null, success = null) }
            val result = runCatchingCancellable {
                api.saveBuilding(
                    id = draft.id,
                    code = draft.code,
                    name = draft.name,
                    floors = floors,
                    sortOrder = requireNotNull(draft.sortOrder.toIntOrNull()),
                    isActive = draft.isActive
                )
            }
            if (result.isFailure) {
                _state.update {
                    it.copy(
                        isSaving = false,
                        error = api.friendlyError(result.exceptionOrNull() ?: IllegalStateException(), "Could not save the building.")
                    )
                }
                return@launch
            }
            _state.update { it.copy(isSaving = false, success = "Building saved.") }
            loadJob = null
            refresh()
            onDone()
        }
    }

    fun saveOrderControls(acceptingOrders: Boolean, maxQty: Int) {
        if (maxQty !in 1..99 || _state.value.isSaving) return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null, success = null) }
            val result = runCatchingCancellable { api.saveOrderControls(acceptingOrders, maxQty) }
            if (result.isFailure) {
                _state.update {
                    it.copy(
                        isSaving = false,
                        error = api.friendlyError(result.exceptionOrNull() ?: IllegalStateException(), "Could not save order controls.")
                    )
                }
                return@launch
            }
            _state.update { it.copy(isSaving = false, success = "Order controls updated.") }
            loadJob = null
            refresh()
        }
    }

    fun reset() {
        loadJob?.cancel()
        loaded = false
        _state.value = DeliverySettingsUiState()
    }
}
