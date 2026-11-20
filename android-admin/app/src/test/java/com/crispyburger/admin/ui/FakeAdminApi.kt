package com.crispyburger.admin.ui

import com.crispyburger.admin.data.AdminApi
import com.crispyburger.admin.data.AdminInventoryItem
import com.crispyburger.admin.data.AdminOrder
import com.crispyburger.admin.data.AdminOrderCounts
import com.crispyburger.admin.data.AdminOrderItem
import com.crispyburger.admin.data.AdminPayment
import com.crispyburger.admin.data.AdminProduct
import com.crispyburger.admin.data.AdminSalesOrder
import com.crispyburger.admin.data.AdminSalesReport
import com.crispyburger.admin.data.ErrorMessages
import java.time.LocalDate

class FakeAdminApi : AdminApi {
    var orders: List<AdminOrder> = emptyList()
    var counts: AdminOrderCounts? = AdminOrderCounts(paymentReview = 2)
    var items: List<AdminOrderItem>? = emptyList()
    var payment: AdminPayment? = null
    var failOrders: Throwable? = null
    var failAction: Throwable? = null

    val calls = mutableListOf<String>()
    var lastLimit = -1

    override suspend fun loadRecentOrders(maxRows: Int): List<AdminOrder> {
        calls += "loadRecentOrders"
        lastLimit = maxRows
        failOrders?.let { throw it }
        return orders
    }

    override suspend fun loadOrderCounts(): AdminOrderCounts? = counts
    override suspend fun loadOrderItems(orderId: String): List<AdminOrderItem>? = items
    override suspend fun loadPayment(orderId: String): AdminPayment? = payment
    override suspend fun createReceiptUrl(path: String): String = "https://example.test/$path"

    private fun act(name: String) {
        calls += name
        failAction?.let { throw it }
    }

    override suspend fun verifyPayment(orderId: String) = act("verify:$orderId")
    override suspend fun rejectPayment(orderId: String, reason: String) = act("reject:$orderId:$reason")
    override suspend fun collectCash(orderId: String) = act("cash:$orderId")
    override suspend fun cancelOrder(orderId: String, reason: String) = act("cancel:$orderId:$reason")
    override suspend fun updateOrderStatus(orderId: String, status: String) = act("status:$orderId:$status")

    override suspend fun loadProducts(): List<AdminProduct> = emptyList()
    override suspend fun updateProductDetails(
        productId: String, sku: String, name: String, description: String, priceCents: Int
    ) = Unit

    override suspend fun loadInventory(): List<AdminInventoryItem> = emptyList()
    override suspend fun adjustInventory(productId: String, type: String, quantity: Int, reason: String) = Unit

    override suspend fun loadSalesReport(from: LocalDate, to: LocalDate): AdminSalesReport =
        throw UnsupportedOperationException()

    override suspend fun loadAllDeliveredOrders(from: LocalDate, to: LocalDate): List<AdminSalesOrder> = emptyList()

    override fun friendlyError(error: Throwable, fallback: String): String = ErrorMessages.map(error, fallback)
}
