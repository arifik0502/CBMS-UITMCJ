package com.crispyburger.admin.data

import java.time.LocalDate

/** Everything the UI layer needs from the backend. ViewModels depend on this, not on Supabase. */
interface AdminApi {
    suspend fun loadRecentOrders(maxRows: Int = 100): List<AdminOrder>
    suspend fun loadOrderCounts(): AdminOrderCounts?
    suspend fun loadOrderItems(orderId: String): List<AdminOrderItem>?
    suspend fun loadPayment(orderId: String): AdminPayment?
    suspend fun createReceiptUrl(path: String): String

    suspend fun verifyPayment(orderId: String)
    suspend fun rejectPayment(orderId: String, reason: String)
    suspend fun collectCash(orderId: String)
    suspend fun cancelOrder(orderId: String, reason: String)
    suspend fun updateOrderStatus(orderId: String, status: String)

    suspend fun loadProducts(): List<AdminProduct>
    suspend fun updateProductDetails(
        productId: String,
        sku: String,
        name: String,
        description: String,
        priceCents: Int
    )

    suspend fun loadInventory(): List<AdminInventoryItem>
    suspend fun adjustInventory(productId: String, type: String, quantity: Int, reason: String)

    suspend fun loadSalesReport(from: LocalDate, to: LocalDate): AdminSalesReport
    suspend fun loadAllDeliveredOrders(from: LocalDate, to: LocalDate): List<AdminSalesOrder>

    fun friendlyError(error: Throwable, fallback: String): String
}
