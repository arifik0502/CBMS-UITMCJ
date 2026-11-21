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

    /** True while the live channel is subscribed (the app is in the foreground). */
    @Volatile
    var isConnected: Boolean = false
        private set

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

            val ticketsFlow = newChannel.postgresChangeFlow<PostgresAction>("public") {
                table = "support_tickets"
            }

            val newScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

            newScope.launch(start = CoroutineStart.UNDISPATCHED) {
                ordersFlow.collect { action ->
                    handleOrders(context, action)
                    AdminDataBus.emit(AdminDataBus.Kind.ORDERS)
                }
            }
            newScope.launch(start = CoroutineStart.UNDISPATCHED) {
                paymentsFlow.collect { action ->
                    handlePayments(context, action)
                    AdminDataBus.emit(AdminDataBus.Kind.PAYMENTS)
                }
            }
            newScope.launch(start = CoroutineStart.UNDISPATCHED) {
                inventoryFlow.collect { action ->
                    handleInventory(context, action)
                    AdminDataBus.emit(AdminDataBus.Kind.INVENTORY)
                }
            }

            newScope.launch(start = CoroutineStart.UNDISPATCHED) {
                ticketsFlow.collect { action ->
                    handleTickets(context, action)
                    AdminDataBus.emit(AdminDataBus.Kind.TICKETS)
                }
            }

            try {
                withTimeout(20_000) {
                    newChannel.subscribe(blockUntilSubscribed = true)
                }
                channel = newChannel
                listenerScope = newScope
                isConnected = true
            } catch (error: Exception) {
                newScope.cancel()
                runCatching { newChannel.unsubscribe() }
                throw error
            }
        }
    }

    suspend fun stop() {
        mutex.withLock {
            isConnected = false
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
        val orderNo = current.value("order_no")
        val label = orderNo?.let { "Order $it" } ?: "An order"

        when (action) {
            is PostgresAction.Insert -> {
                AdminNotificationHelper.post(
                    context = context,
                    eventKey = "order:new:$orderId",
                    title = "New order received",
                    body = if (orderNo != null) "$label is ready for review." else "A new CBMS order is ready for review.",
                    isPayment = false,
                    orderId = orderId
                )
            }
            is PostgresAction.Update -> {
                val old = action.oldRecord
                val newPayment = current.value("payment_status")
                val newOrder = current.value("order_status")
                when (
                    AdminRealtimeEventClassifier.orderUpdate(
                        oldPaymentStatus = old.value("payment_status"),
                        newPaymentStatus = newPayment,
                        oldOrderStatus = old.value("order_status"),
                        newOrderStatus = newOrder
                    )
                ) {
                    AdminRealtimeNotice.PAYMENT_STATUS -> {
                        AdminNotificationHelper.post(
                            context = context,
                            eventKey = "payment:$orderId:${newPayment.orEmpty()}",
                            title = "Payment status updated",
                            body = "$label payment is now ${PaymentStatus.label(newPayment.orEmpty())}.",
                            isPayment = true,
                            orderId = orderId
                        )
                    }
                    AdminRealtimeNotice.ORDER_STATUS -> {
                        AdminNotificationHelper.post(
                            context = context,
                            eventKey = "order-status:$orderId:${newOrder.orEmpty()}",
                            title = "Order status updated",
                            body = "$label is now ${OrderStatus.label(newOrder.orEmpty())}.",
                            isPayment = false,
                            orderId = orderId
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
            eventKey = "payment:$orderId:receipt",
            title = "Payment receipt uploaded",
            body = "A payment receipt is ready for verification.",
            isPayment = true,
            orderId = orderId
        )
    }

    /** New ticket inserts notify the owner and refresh the ticket list. */
    private fun handleTickets(context: Context, action: PostgresAction) {
        if (action !is PostgresAction.Insert) return
        val record = action.record
        val ticketId = record.value("id") ?: return
        val ticketNo = record.value("ticket_no") ?: "A ticket"
        val name = record.value("customer_name")?.take(40)
        val preview = record.value("message")?.lineSequence()?.firstOrNull()?.take(100).orEmpty()

        AdminNotificationHelper.post(
            context = context,
            eventKey = "ticket:$ticketId",
            title = "New support ticket",
            body = buildString {
                append(ticketNo)
                if (!name.isNullOrBlank()) append(" from ").append(name)
                if (preview.isNotBlank()) append(": ").append(preview)
            },
            isPayment = false,
            openTab = AdminNotificationHelper.TAB_TICKETS
        )
    }

    /** Only low-stock crossings notify; routine restocks, deliveries and own adjustments stay quiet. */
    private fun handleInventory(context: Context, action: PostgresAction) {
        if (action !is PostgresAction.Update) return

        val productId = action.record.value("product_id") ?: return
        val newAvailable = action.record.available()

        if (!AdminRealtimeEventClassifier.crossedLowStock(action.oldRecord.available(), newAvailable)) return

        AdminNotificationHelper.post(
            context = context,
            eventKey = "inventory:low:$productId",
            title = "Low stock",
            body = "A product is running low: $newAvailable available.",
            isPayment = false
        )
    }

    private fun JsonObject.available(): Int? =
        value("available_stock")?.toIntOrNull() ?: run {
            val physical = value("physical_stock")?.toIntOrNull()
            val reserved = value("reserved_stock")?.toIntOrNull()
            if (physical != null && reserved != null) physical - reserved else null
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
