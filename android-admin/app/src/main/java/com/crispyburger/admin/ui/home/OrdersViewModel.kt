package com.crispyburger.admin.ui.home

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crispyburger.admin.data.AdminApi
import com.crispyburger.admin.data.AdminDataBus
import com.crispyburger.admin.data.AdminOrder
import com.crispyburger.admin.data.AdminOrderCounts
import com.crispyburger.admin.data.AdminOrderItem
import com.crispyburger.admin.data.AdminPayment
import com.crispyburger.admin.data.AdminSupabase
import com.crispyburger.admin.data.OrderFilter
import com.crispyburger.admin.data.ReceiptLoader
import com.crispyburger.admin.data.runCatchingCancellable
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ItemsState {
    data object None : ItemsState
    data object Loading : ItemsState
    data class Loaded(val items: List<AdminOrderItem>) : ItemsState
    data object Unavailable : ItemsState
}

sealed interface ReceiptState {
    data object None : ReceiptState
    data object Loading : ReceiptState
    data class Ready(val bitmap: Bitmap, val url: String) : ReceiptState
    data class Failed(val url: String) : ReceiptState
}

sealed interface OrderAction {
    data object Verify : OrderAction
    data object CollectCash : OrderAction
    data class Advance(val status: String) : OrderAction
    data class Reject(val reason: String) : OrderAction
    data class Cancel(val reason: String) : OrderAction
}

data class OrdersUiState(
    val orders: List<AdminOrder> = emptyList(),
    val counts: AdminOrderCounts? = null,
    val isLoading: Boolean = false,
    val isWorking: Boolean = false,
    val error: String? = null,
    val info: String? = null,
    val search: String = "",
    val filter: OrderFilter = OrderFilter.ACTIVE,
    val fetchLimit: Int = OrdersViewModel.PAGE_SIZE,
    val canLoadMore: Boolean = false,
    val selectedOrderId: String? = null,
    val items: ItemsState = ItemsState.None,
    val payment: AdminPayment? = null,
    val paymentLoading: Boolean = false,
    val receipt: ReceiptState = ReceiptState.None
) {
    val selectedOrder: AdminOrder?
        get() = selectedOrderId?.let { id -> orders.firstOrNull { it.id == id } }
}

class OrdersViewModel(
    private val api: AdminApi = AdminSupabase,
    private val receiptDownloader: suspend (String) -> Bitmap? = { ReceiptLoader.download(it) }
) : ViewModel() {

    private val _state = MutableStateFlow(OrdersUiState())
    val state: StateFlow<OrdersUiState> = _state.asStateFlow()

    private var loaded = false
    private var loadJob: Job? = null
    private var reloadQueued = false
    private var detailJob: Job? = null
    private var pendingOpenOrderId: String? = null

    init {
        observeChanges()
    }

    @OptIn(FlowPreview::class)
    private fun observeChanges() {
        viewModelScope.launch {
            AdminDataBus.changes
                .filter {
                    it.origin !== this@OrdersViewModel &&
                        (it.kind == AdminDataBus.Kind.ORDERS || it.kind == AdminDataBus.Kind.PAYMENTS)
                }
                .debounce(DEBOUNCE_MS)
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
                load(silent)
            } while (reloadQueued)
        }
    }

    private suspend fun load(silent: Boolean) {
        if (!silent) _state.update { it.copy(isLoading = true, error = null) }
        val limit = _state.value.fetchLimit

        val ordersResult = runCatchingCancellable { api.loadRecentOrders(limit) }
        val counts = if (ordersResult.isSuccess) {
            runCatchingCancellable { api.loadOrderCounts() }.getOrNull()
        } else {
            null
        }

        ordersResult
            .onSuccess { orders ->
                val previous = _state.value.selectedOrder
                val latest = previous?.let { old -> orders.firstOrNull { it.id == old.id } }
                if (previous != null && latest != null && (latest.customerEditRevision != previous.customerEditRevision || (latest.orderStatus == "CANCELLED" && previous.orderStatus != "CANCELLED"))) {
                    dismissDetail()
                    _state.update { it.copy(info = "Customer booking changed. Reopen the order to review current details.") }
                }
                _state.update {
                    it.copy(
                        orders = orders,
                        counts = counts,
                        isLoading = false,
                        error = null,
                        canLoadMore = orders.size >= limit && limit < MAX_FETCH
                    )
                }
                openPendingIfPossible()
            }
            .onFailure { error ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = api.friendlyError(
                            error,
                            "Could not load orders. Check your connection and database setup."
                        )
                    )
                }
            }
    }

    fun loadMore() {
        _state.update { it.copy(fetchLimit = minOf(it.fetchLimit + PAGE_SIZE, MAX_FETCH)) }
        refresh()
    }

    fun setSearch(value: String) = _state.update { it.copy(search = value) }

    fun setFilter(value: OrderFilter) = _state.update { it.copy(filter = value) }

    fun clearMessages() = _state.update { it.copy(info = null, error = null) }

    /** Called when a notification is tapped: opens that order once it is loaded. */
    fun requestOpen(orderId: String) {
        pendingOpenOrderId = orderId
        if (!openPendingIfPossible()) refresh(silent = true)
    }

    private fun openPendingIfPossible(): Boolean {
        val id = pendingOpenOrderId ?: return false
        if (_state.value.orders.none { it.id == id }) return false
        pendingOpenOrderId = null
        select(id)
        return true
    }

    fun select(orderId: String) {
        detailJob?.cancel()
        _state.update {
            it.copy(
                selectedOrderId = orderId,
                items = ItemsState.Loading,
                payment = null,
                paymentLoading = true,
                receipt = ReceiptState.None,
                error = null
            )
        }
        detailJob = viewModelScope.launch {
            coroutineScope {
                launch {
                    val items = runCatchingCancellable { api.loadOrderItems(orderId) }.getOrNull()
                    _state.update {
                        if (it.selectedOrderId != orderId) it
                        else it.copy(items = if (items == null) ItemsState.Unavailable else ItemsState.Loaded(items))
                    }
                }
                launch {
                    val result = runCatchingCancellable { api.loadPayment(orderId) }
                    _state.update {
                        if (it.selectedOrderId != orderId) it
                        else it.copy(
                            payment = result.getOrNull(),
                            paymentLoading = false,
                            error = result.exceptionOrNull()?.let { e ->
                                api.friendlyError(e, "Could not load payment details.")
                            } ?: it.error
                        )
                    }
                }
            }
        }
    }

    fun dismissDetail() {
        detailJob?.cancel()
        _state.update {
            it.copy(
                selectedOrderId = null,
                items = ItemsState.None,
                payment = null,
                paymentLoading = false,
                receipt = ReceiptState.None
            )
        }
    }

    fun perform(action: OrderAction) {
        val orderId = _state.value.selectedOrderId ?: return
        if (_state.value.isWorking) return

        viewModelScope.launch {
            _state.update { it.copy(isWorking = true, error = null, info = null) }

            // The action and the reload are separate: a failed reload must never look like a failed action.
            val result = runCatchingCancellable {
                when (action) {
                    OrderAction.Verify -> api.verifyPayment(orderId)
                    OrderAction.CollectCash -> api.collectCash(orderId)
                    is OrderAction.Advance -> api.updateOrderStatus(orderId, action.status)
                    is OrderAction.Reject -> api.rejectPayment(orderId, action.reason)
                    is OrderAction.Cancel -> api.cancelOrder(orderId, action.reason)
                }
            }

            result.onFailure { error ->
                _state.update {
                    it.copy(
                        isWorking = false,
                        error = api.friendlyError(
                            error,
                            "The action could not be completed. Check your connection and access permissions."
                        )
                    )
                }
            }
            if (result.isFailure) return@launch

            _state.update { it.copy(isWorking = false, info = "Order updated successfully.") }
            dismissDetail()
            AdminDataBus.emit(AdminDataBus.Kind.ORDERS, origin = this@OrdersViewModel)
            AdminDataBus.emit(AdminDataBus.Kind.PAYMENTS, origin = this@OrdersViewModel)
            refresh(silent = true)
        }
    }

    fun viewReceipt() {
        val path = _state.value.payment?.receiptPath ?: return
        viewModelScope.launch {
            _state.update { it.copy(receipt = ReceiptState.Loading, error = null) }
            val urlResult = runCatchingCancellable { api.createReceiptUrl(path) }
            val url = urlResult.getOrNull()
            if (url == null) {
                _state.update {
                    it.copy(
                        receipt = ReceiptState.None,
                        error = urlResult.exceptionOrNull()?.let { e ->
                            api.friendlyError(e, "Could not open the private receipt.")
                        }
                    )
                }
                return@launch
            }
            val bitmap = receiptDownloader(url)
            _state.update {
                it.copy(receipt = if (bitmap != null) ReceiptState.Ready(bitmap, url) else ReceiptState.Failed(url))
            }
        }
    }

    fun dismissReceipt() = _state.update { it.copy(receipt = ReceiptState.None) }

    /** Clears everything (called on sign-out so the next login never sees stale data). */
    fun reset() {
        loadJob?.cancel()
        detailJob?.cancel()
        loaded = false
        reloadQueued = false
        pendingOpenOrderId = null
        _state.value = OrdersUiState()
    }

    companion object {
        const val PAGE_SIZE = 100
        const val MAX_FETCH = 1000
        const val DEBOUNCE_MS = 800L
    }
}
