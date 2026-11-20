package com.crispyburger.admin.data

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * In-process change notifications. The realtime manager and ViewModels publish here;
 * each ViewModel refreshes only the sections its data belongs to (debounced).
 */
object AdminDataBus {
    enum class Kind { ORDERS, PAYMENTS, INVENTORY, PRODUCTS }

    data class Change(val kind: Kind, val origin: Any? = null)

    private val flow = MutableSharedFlow<Change>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val changes: SharedFlow<Change> = flow.asSharedFlow()

    fun emit(kind: Kind, origin: Any? = null) {
        flow.tryEmit(Change(kind, origin))
    }
}
