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
    suspend fun createProduct(
        sku: String,
        name: String,
        description: String,
        priceCents: Int,
        imageUrl: String?,
        initialStock: Int
    )
    suspend fun updateProductDetails(
        productId: String,
        sku: String,
        name: String,
        description: String,
        priceCents: Int,
        imageUrl: String?,
        isActive: Boolean
    )
    /** Uploads a JPEG to the public product-images bucket and returns its public URL. */
    suspend fun uploadProductImage(jpeg: ByteArray): String

    suspend fun loadDeliverySettings(): AdminDeliverySettings
    suspend fun saveDeliveryTrip(
        id: String?,
        tripNo: Int,
        label: String,
        startTime: String,
        endTime: String,
        isOpen: Boolean
    )
    suspend fun saveBuilding(
        id: String?,
        code: String,
        name: String,
        floors: List<Int>,
        sortOrder: Int,
        isActive: Boolean
    )
    suspend fun saveOrderControls(acceptingOrders: Boolean, maxQtyPerOrder: Int)

    suspend fun loadInventory(): List<AdminInventoryItem>
    suspend fun adjustInventory(productId: String, type: String, quantity: Int, reason: String)

    suspend fun loadTickets(maxRows: Int = 200): List<AdminTicket>
    suspend fun setTicketStatus(ticketId: String, status: String)
    suspend fun createTicketPhotoUrl(path: String): String

    suspend fun loadSalesReport(from: LocalDate, to: LocalDate): AdminSalesReport
    suspend fun loadAllDeliveredOrders(from: LocalDate, to: LocalDate): List<AdminSalesOrder>

    // Website customization (see supabase/migrations/20261123000001_site_customization.sql)
    suspend fun loadSiteState(): SiteState
    suspend fun saveSiteDraft(config: SiteConfig)
    suspend fun publishSite()
    suspend fun discardSiteDraft()
    suspend fun loadSiteHistory(): List<SiteVersion>
    suspend fun restoreSiteVersion(id: String)
    /** Uploads a JPEG to the public site-assets bucket and returns its public URL. */
    suspend fun uploadSiteImage(jpeg: ByteArray): String

    fun friendlyError(error: Throwable, fallback: String): String
}
