package com.crispyburger.admin.data

import android.content.Context
import com.crispyburger.admin.notifications.AdminNotificationHelper
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

object AdminRealtimeManager {
    private val mutex = Mutex()
    private var channel: RealtimeChannel? = null
    private var listenerScope: CoroutineScope? = null

    private val mutableDataChanges = MutableStateFlow(0)
    val dataChanges: StateFlow<Int> = mutableDataChanges.asStateFlow()

    suspend fun start(context: Context) {
        mutex.withLock {
            if (channel != null) return

            AdminSupabase.verifyDeveloperSession()
            val client = AdminSupabase.realtimeClient()
            val userId = client.auth.currentUserOrNull()?.id
                ?: throw SecurityException("Sign in again to receive live updates.")

            val newChannel: RealtimeChannel = client.realtime.channel("cbms-admin-$userId")
            val ordersFlow = newChannel.postgresChangeFlow<PostgresAction>("public") {
                table = "orders"
            }
            val paymentsFlow = newChannel.postgresChangeFlow<PostgresAction>("public") {
                table = "payments"
            }
            val inventoryFlow = newChannel.postgresChangeFlow<PostgresAction>("public") {
                table = "inventory"
            }

            val newScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

            newScope.launch(start = CoroutineStart.UNDISPATCHED) {
                ordersFlow.collect { action ->
                    handleOrders(context, action)
                    mutableDataChanges.value += 1
                }
            }
            newScope.launch(start = CoroutineStart.UNDISPATCHED) {
                paymentsFlow.collect { action ->
                    handlePayments(context, action)
                    mutableDataChanges.value += 1
                }
            }
            newScope.launch(start = CoroutineStart.UNDISPATCHED) {
                inventoryFlow.collect { action ->
                    handleInventory(context, action)
                    mutableDataChanges.value += 1
                }
            }

            try {
                withTimeout(20_000) {
                    newChannel.subscribe(blockUntilSubscribed = true)
                }
                channel = newChannel
                listenerScope = newScope
            } catch (error: Exception) {
                newScope.cancel()
                runCatching { newChannel.unsubscribe() }
                throw error
            }
        }
    }

    suspend fun stop() {
        mutex.withLock {
            listenerScope?.cancel()
            listenerScope = null

            val activeChannel = channel
            channel = null

            if (activeChannel != null) {
                runCatching { activeChannel.unsubscribe() }
                runCatching {
                    activeChannel.realtime.removeChannel(activeChannel)
                }
            }
        }
    }

    private fun handleOrders(context: Context, action: PostgresAction) {
        val current = action.currentRecord() ?: return
        val orderId = current.value("id") ?: return
        val timestamp = action.commitTimestamp.toString()

        when (action) {
            is PostgresAction.Insert -> {
                AdminNotificationHelper.post(
                    context = context,
                    eventKey = "order:new:$orderId:$timestamp",
                    title = "New order received",
                    body = "A new CBMS order is ready for review."
                )
            }
            is PostgresAction.Update -> {
                val old = action.oldRecord
                when (
                    AdminRealtimeEventClassifier.orderUpdate(
                        oldPaymentStatus = old.value("payment_status"),
                        newPaymentStatus = current.value("payment_status"),
                        oldOrderStatus = old.value("order_status"),
                        newOrderStatus = current.value("order_status")
                    )
                ) {
                    AdminRealtimeNotice.PAYMENT_STATUS -> {
                        AdminNotificationHelper.post(
                            context = context,
                            eventKey = "payment:$orderId:$timestamp",
                            title = "Payment status updated",
                            body = "The payment status for an order has changed."
                        )
                    }
                    AdminRealtimeNotice.ORDER_STATUS -> {
                        AdminNotificationHelper.post(
                            context = context,
                            eventKey = "order-status:$orderId:$timestamp",
                            title = "Order status updated",
                            body = "An order has moved to another preparation or delivery stage."
                        )
                    }
                    null -> Unit
                }
            }
            else -> Unit
        }
    }

    private fun handlePayments(context: Context, action: PostgresAction) {
        if (action !is PostgresAction.Update) return

        val orderId = action.record.value("order_id") ?: return
        val oldReceipt = action.oldRecord.value("receipt_path")
        val newReceipt = action.record.value("receipt_path")

        if (!AdminRealtimeEventClassifier.paymentReceiptUpdated(oldReceipt, newReceipt)) return

        AdminNotificationHelper.post(
            context = context,
            eventKey = "payment:$orderId:${action.commitTimestamp}",
            title = "Payment receipt uploaded",
            body = "A payment receipt is ready for verification."
        )
    }

    private fun handleInventory(context: Context, action: PostgresAction) {
        if (action !is PostgresAction.Update) return

        val old = action.oldRecord
        val current = action.record
        val productId = current.value("product_id") ?: return

        if (!AdminRealtimeEventClassifier.physicalStockUpdated(
                old.value("physical_stock"),
                current.value("physical_stock")
            )
        ) return

        AdminNotificationHelper.post(
            context = context,
            eventKey = "inventory:$productId:${action.commitTimestamp}",
            title = "Inventory stock changed",
            body = "A product's physical stock quantity has changed."
        )
    }

    private fun PostgresAction.currentRecord(): JsonObject? {
        return when (this) {
            is PostgresAction.Insert -> record
            is PostgresAction.Update -> record
            is PostgresAction.Select -> record
            is PostgresAction.Delete -> null
        }
    }

    private fun JsonObject.value(key: String): String? {
        return this[key]?.jsonPrimitive?.contentOrNull
    }
}
