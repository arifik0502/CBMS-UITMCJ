package com.crispyburger.admin.data

import java.time.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeveloperProfile(
    val id: String,
    @SerialName("full_name") val fullName: String = "",
    val role: String,
    @SerialName("is_active") val isActive: Boolean
)

@Serializable
data class AdminOrder(
    val id: String,
    @SerialName("order_no") val orderNo: String,
    @SerialName("customer_name") val customerName: String,
    @SerialName("customer_phone") val customerPhone: String,
    @SerialName("building_id") val buildingId: String,
    @SerialName("floor_number") val floorNumber: Int,
    @SerialName("room_number") val roomNumber: String,
    @SerialName("payment_method") val paymentMethod: String,
    @SerialName("order_status") val orderStatus: String,
    @SerialName("payment_status") val paymentStatus: String,
    @SerialName("total_cents") val totalCents: Int,
    @SerialName("created_at") val createdAt: String,
    @SerialName("delivered_at") val deliveredAt: String? = null,
    @SerialName("trip_id") val tripId: String? = null,
    @SerialName("trip_date") val tripDate: String? = null,
    @SerialName("note") val note: String? = null,
    val buildingLabel: String = "",
    val tripLabel: String = "",
    val tripWindow: String = "",
    val tripNo: Int = 0
)

@Serializable
data class AdminDeliveryTrip(
    val id: String,
    @SerialName("trip_no") val tripNo: Int,
    val label: String,
    @SerialName("start_time") val startTime: String,
    @SerialName("end_time") val endTime: String
)

@Serializable
data class AdminBuilding(
    val id: String,
    val code: String,
    val name: String
)

@Serializable
data class AdminPayment(
    @SerialName("order_id") val orderId: String,
    @SerialName("receipt_path") val receiptPath: String? = null,
    @SerialName("rejection_reason") val rejectionReason: String? = null
)

@Serializable
data class AdminItemProduct(val name: String = "")

/** One line of an order. Loaded on demand when an order is opened. */
@Serializable
data class AdminOrderItem(
    val quantity: Int = 0,
    @SerialName("unit_price_cents") val unitPriceCents: Long = 0,
    @SerialName("line_total_cents") val lineTotalCents: Long = 0,
    val products: AdminItemProduct? = null
) {
    val productName: String
        get() = products?.name?.takeIf { it.isNotBlank() } ?: "Item"

    val displayTotalCents: Long
        get() = if (lineTotalCents > 0) lineTotalCents else unitPriceCents * quantity
}

data class AdminCashCollection(
    val orderNo: String,
    val amountCents: Long,
    val collectedAt: String
)

@Serializable
data class AdminInventoryStock(
    @SerialName("product_id") val productId: String,
    @SerialName("physical_stock") val physicalStock: Int,
    @SerialName("reserved_stock") val reservedStock: Int,
    @SerialName("available_stock") val availableStock: Int
)

@Serializable
data class AdminProduct(
    val id: String,
    val sku: String,
    val name: String,
    val description: String = "",
    @SerialName("price_cents") val priceCents: Int,
    @SerialName("is_active") val isActive: Boolean = true
)

data class AdminInventoryItem(
    val product: AdminProduct,
    val stock: AdminInventoryStock
)

@Serializable
data class AdminSalesOrder(
    val id: String,
    @SerialName("order_no") val orderNo: String,
    @SerialName("payment_status") val paymentStatus: String,
    @SerialName("payment_method") val paymentMethod: String,
    @SerialName("total_cents") val totalCents: Int,
    @SerialName("delivered_at") val deliveredAt: String
)

/** Server-side order counts (whole table, not just the loaded page). */
@Serializable
data class AdminOrderCounts(
    @SerialName("payment_review") val paymentReview: Int = 0,
    @SerialName("new_orders") val newOrders: Int = 0,
    @SerialName("in_progress") val inProgress: Int = 0,
    @SerialName("delivered_today") val deliveredToday: Int = 0
) {
    companion object {
        /** Approximation from the loaded page; used only if the server RPC is unavailable. */
        fun fromOrders(
            orders: List<AdminOrder>,
            today: LocalDate = ReportRange.today()
        ): AdminOrderCounts = AdminOrderCounts(
            paymentReview = orders.count { it.paymentStatus == PaymentStatus.PENDING_VERIFICATION },
            newOrders = orders.count { it.orderStatus in OrderStatus.incoming },
            inProgress = orders.count { it.orderStatus in OrderStatus.inProgress },
            deliveredToday = orders.count { Formats.malaysiaLocalDate(it.deliveredAt) == today }
        )
    }
}

data class AdminSalesReport(
    val summary: SalesReportSummary,
    val recentOrders: List<AdminSalesOrder>,
    val cashCollections: List<AdminCashCollection>,
    val cashCollectedCents: Long,
    val cashCollectionCount: Int,
    /** True when the server aggregate was unavailable and totals were computed from a capped download. */
    val approximate: Boolean
)

@Serializable
data class AdminTicketOrder(
    @SerialName("order_no") val orderNo: String = ""
)

/** A customer support ticket (one-way report; the owner only sets Open / Resolved). */
@Serializable
data class AdminTicket(
    val id: String,
    @SerialName("ticket_no") val ticketNo: String,
    val message: String,
    @SerialName("customer_name") val customerName: String,
    @SerialName("customer_phone") val customerPhone: String,
    @SerialName("attachment_path") val attachmentPath: String? = null,
    val status: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("resolved_at") val resolvedAt: String? = null,
    /** Embedded order (null for general questions, or if the order was deleted). */
    val orders: AdminTicketOrder? = null
) {
    val orderNo: String? get() = orders?.orderNo?.takeIf { it.isNotBlank() }
}
