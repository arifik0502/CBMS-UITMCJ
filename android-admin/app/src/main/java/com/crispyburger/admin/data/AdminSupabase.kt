package com.crispyburger.admin.data

import com.crispyburger.admin.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.storage
import kotlin.time.Duration.Companion.minutes
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

const val DEVELOPER_EMAIL = "ariefffikri936@gmail.com"

private fun formatTripTime(value: String): String {
    val parts = value.take(5).split(":")
    if (parts.size != 2) return value
    val hour = parts[0].toIntOrNull() ?: return value
    val minute = parts[1]
    val displayHour = when (hour % 12) { 0 -> 12; else -> hour % 12 }
    return "$displayHour:$minute ${if (hour < 12) "AM" else "PM"}"
}

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
    @SerialName("assigned_seller_id") val assignedSellerId: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("delivered_at") val deliveredAt: String? = null,
    @SerialName("trip_id") val tripId: String? = null,
    @SerialName("trip_date") val tripDate: String? = null,
    @SerialName("note") val note: String? = null,
    val buildingLabel: String = "",
    val tripLabel: String = "",
    val tripWindow: String = ""
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
private data class AdminCashCollectionRow(
    @SerialName("order_id") val orderId: String,
    @SerialName("amount_cents") val amountCents: Int,
    @SerialName("collected_at") val collectedAt: String
)

@Serializable
private data class AdminCashCollectedOrder(
    val id: String,
    @SerialName("order_no") val orderNo: String
)

data class AdminCashCollection(
    val orderNo: String,
    val amountCents: Int,
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

@Serializable
private data class AdminDeviceTokenUpsert(
    @SerialName("user_id") val userId: String,
    val token: String,
    val platform: String = "android",
    @SerialName("last_seen_at") val lastSeenAt: String
)

object AdminSupabase {
    val isConfigured: Boolean
        get() = BuildConfig.SUPABASE_URL.isNotBlank() &&
            BuildConfig.SUPABASE_ANON_KEY.isNotBlank()

    fun friendlyError(error: Throwable, fallback: String): String {
        val message = error.message.orEmpty().lowercase()
        return when {
            message.contains("trip_date") &&
                (message.contains("does not exist") || message.contains("42703")) ->
                "The Supabase database is missing the CBMS trips migration. Check migration 20261116000001_trips_notes_commission.sql, then refresh."

            message.contains("does not exist") &&
                (message.contains("column") || message.contains("relation")) ->
                "The Supabase database schema is behind this app. Check that all required migrations have been applied."

            message.contains("forbidden") || message.contains("42501") ->
                "Your developer account is not authorized to perform this action. Confirm that its OWNER profile is active and that the latest database migration has been applied."

            message.contains("invalid_payment_state") ->
                "This order is no longer awaiting cash collection. Refresh the order and check its payment status."

            message.contains("below_reserved") ->
                "Inventory adjustment rejected: physical stock cannot be lower than reserved stock. Refresh inventory and check the latest physical and reserved quantities. If this happened during Restock while physical stock was already above reserved stock, the database inventory row may differ from the displayed values."

            message.contains("not_out_for_delivery") ->
                "The live collect_cash function still has the old delivery restriction. Apply migration 20261117000005_owner_cash_payment_controls.sql in Supabase, then retry."

            message.contains("order_closed") ->
                "This order is cancelled or closed and cannot accept cash."

            message.contains("order_not_found") ->
                "This order could not be found. Refresh the order list."

            message.contains("permission denied") ->
                "Supabase denied this operation. Apply the latest CBMS permissions migration and verify the active developer OWNER account."

            else -> "$fallback Details: ${error.message.orEmpty().take(220)}"
        }
    }

    private val client: SupabaseClient by lazy {
        require(isConfigured) {
            "Supabase is not configured. Add supabase.url and supabase.anonKey to local.properties."
        }

        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY
        ) {
            install(Auth)
            install(Postgrest)
            install(Realtime)
        }
    }

    suspend fun signInDeveloper(password: String): DeveloperProfile {
        require(password.isNotBlank()) { "Enter your password." }

        client.auth.signInWith(Email) {
            email = DEVELOPER_EMAIL
            this.password = password
        }

        return requireDeveloperProfile()
    }

    suspend fun restoreDeveloperSession(): DeveloperProfile? {
        if (!isConfigured) return null

        client.auth.awaitInitialization()
        val user = client.auth.currentUserOrNull() ?: return null
        if (!user.email.equals(DEVELOPER_EMAIL, ignoreCase = true)) {
            client.auth.signOut()
            return null
        }

        return try {
            requireDeveloperProfile()
        } catch (_: Exception) {
            client.auth.signOut()
            null
        }
    }

    suspend fun signOut(deviceToken: String? = null) {
        if (!isConfigured) return
        if (!deviceToken.isNullOrBlank()) {
            runCatching {
                client.from("device_tokens").delete {
                    filter { eq("token", deviceToken) }
                }
            }
        }
        client.auth.signOut()
    }

    suspend fun registerDeviceToken(token: String) {
        require(token.isNotBlank()) { "FCM token is empty." }
        requireDeveloperSession()
        val user = client.auth.currentUserOrNull()
            ?: throw SecurityException("Sign in before registering this device.")

        client.from("device_tokens").upsert(
            AdminDeviceTokenUpsert(
                userId = user.id,
                token = token,
                lastSeenAt = Instant.now().toString()
            )
        ) {
            onConflict = "token"
        }
    }

    suspend fun loadRecentOrders(): List<AdminOrder> {
        requireDeveloperSession()

        val orders = client.from("orders")
            .select(
                columns = Columns.list(
                    "id",
                    "order_no",
                    "customer_name",
                    "customer_phone",
                    "building_id",
                    "floor_number",
                    "room_number",
                    "payment_method",
                    "order_status",
                    "payment_status",
                    "total_cents",
                    "assigned_seller_id",
                    "created_at",
                    "delivered_at",
                    "trip_id",
                    "trip_date",
                    "note"
                )
            ) {
                order("created_at", Order.DESCENDING)
                limit(100)
            }
            .decodeList<AdminOrder>()

        val buildings = client.from("buildings")
            .select(columns = Columns.list("id", "code", "name"))
            .decodeList<AdminBuilding>()
            .associateBy { it.id }

        val trips = client.from("delivery_trips")
            .select(columns = Columns.list("id", "trip_no", "label", "start_time", "end_time"))
            .decodeList<AdminDeliveryTrip>()
            .associateBy { it.id }

        return orders.map { order ->
            val building = buildings[order.buildingId]
            val trip = order.tripId?.let { trips[it] }
            order.copy(
                buildingLabel = if (building == null) {
                    "Unknown building"
                } else {
                    "${building.code} - ${building.name}"
                },
                tripLabel = trip?.label ?: order.tripId?.let { "Trip unavailable" } ?: "No trip assigned",
                tripWindow = trip?.let {
                    "${formatTripTime(it.startTime)} – ${formatTripTime(it.endTime)}"
                } ?: ""
            )
        }
    }

    suspend fun loadDeliveredSales(
        fromDate: LocalDate,
        toDate: LocalDate
    ): List<AdminSalesOrder> {
        requireDeveloperSession()
        require(!toDate.isBefore(fromDate)) { "End date must be on or after start date." }
        require(toDate.toEpochDay() - fromDate.toEpochDay() <= 366) {
            "Report range cannot exceed 367 calendar days."
        }

        val malaysiaZone = ZoneId.of("Asia/Kuala_Lumpur")
        val fromInstant = fromDate.atStartOfDay(malaysiaZone).toInstant().toString()
        val toExclusive = toDate.plusDays(1).atStartOfDay(malaysiaZone).toInstant().toString()

        return client.from("orders")
            .select(columns = Columns.list(
                "id",
                "order_no",
                "payment_status",
                "payment_method",
                "total_cents",
                "delivered_at"
            )) {
                filter {
                    gte("delivered_at", fromInstant)
                    lt("delivered_at", toExclusive)
                }
                order("delivered_at", Order.DESCENDING)
                limit(500)
            }
            .decodeList<AdminSalesOrder>()
    }

    suspend fun loadCashCollections(
        fromDate: LocalDate,
        toDate: LocalDate
    ): List<AdminCashCollection> {
        requireDeveloperSession()
        require(!toDate.isBefore(fromDate)) { "End date must be on or after start date." }

        val malaysiaZone = ZoneId.of("Asia/Kuala_Lumpur")
        val fromInstant = fromDate.atStartOfDay(malaysiaZone).toInstant().toString()
        val toExclusive = toDate.plusDays(1).atStartOfDay(malaysiaZone).toInstant().toString()

        val payments = client.from("payments")
            .select(columns = Columns.list("order_id", "amount_cents", "collected_at")) {
                filter {
                    gte("collected_at", fromInstant)
                    lt("collected_at", toExclusive)
                }
                order("collected_at", Order.DESCENDING)
                limit(500)
            }
            .decodeList<AdminCashCollectionRow>()

        if (payments.isEmpty()) return emptyList()

        val collectedOrders = client.from("orders")
            .select(columns = Columns.list("id", "order_no")) {
                filter {
                    eq("payment_status", "CASH_COLLECTED")
                }
                order("created_at", Order.DESCENDING)
                limit(1000)
            }
            .decodeList<AdminCashCollectedOrder>()
            .associateBy { it.id }

        return payments.mapNotNull { payment ->
            val order = collectedOrders[payment.orderId] ?: return@mapNotNull null
            AdminCashCollection(
                orderNo = order.orderNo,
                amountCents = payment.amountCents,
                collectedAt = payment.collectedAt
            )
        }
    }

    suspend fun loadProducts(): List<AdminProduct> {
        requireDeveloperSession()
        return client.from("products")
            .select(columns = Columns.list(
                "id",
                "sku",
                "name",
                "description",
                "price_cents",
                "is_active"
            )) {
                order("name", Order.ASCENDING)
            }
            .decodeList<AdminProduct>()
    }

    suspend fun updateProductDetails(
        productId: String,
        sku: String,
        name: String,
        description: String,
        priceCents: Int
    ) {
        requireDeveloperSession()
        val normalizedSku = ProductValidation.normalizeSku(sku)
            ?: throw IllegalArgumentException("SKU must be 2-20 letters, numbers, hyphens or underscores.")
        require(name.trim().length in 2..80) { "Product name must be 2-80 characters." }
        require(description.trim().length <= 500) { "Description must be 500 characters or fewer." }
        require(priceCents in 1..1_000_000) { "Enter a valid price." }

        client.postgrest.rpc(
            "update_product_details",
            buildJsonObject {
                put("p_product_id", productId)
                put("p_sku", normalizedSku)
                put("p_name", name.trim())
                put("p_description", description.trim())
                put("p_price_cents", priceCents)
            }
        )
    }

    suspend fun loadInventory(): List<AdminInventoryItem> {
        requireDeveloperSession()

        val stockRows = client.from("inventory")
            .select(columns = Columns.list(
                "product_id",
                "physical_stock",
                "reserved_stock",
                "available_stock"
            ))
            .decodeList<AdminInventoryStock>()

        val products = client.from("products")
            .select(columns = Columns.list(
                "id",
                "sku",
                "name",
                "description",
                "price_cents"
            ))
            .decodeList<AdminProduct>()
            .associateBy { it.id }

        return stockRows.mapNotNull { stock ->
            val product = products[stock.productId] ?: return@mapNotNull null
            AdminInventoryItem(product, stock)
        }.sortedBy { it.product.name.lowercase() }
    }

    suspend fun adjustInventory(
        productId: String,
        type: String,
        quantity: Int,
        reason: String
    ) {
        requireDeveloperSession()
        require(reason.trim().length >= 3) { "Enter an adjustment reason." }
        require(quantity >= 0) { "Quantity cannot be negative." }
        client.postgrest.rpc(
            "adjust_inventory",
            buildJsonObject {
                put("p_product_id", productId)
                put("p_type", type)
                put("p_quantity", quantity)
                put("p_reason", reason.trim())
            }
        )
    }

    suspend fun loadPayment(orderId: String): AdminPayment? {
        requireDeveloperSession()
        return client.from("payments")
            .select(columns = Columns.list("order_id", "receipt_path", "rejection_reason")) {
                filter {
                    eq("order_id", orderId)
                }
            }
            .decodeList<AdminPayment>()
            .firstOrNull()
    }

    suspend fun createReceiptUrl(path: String): String {
        requireDeveloperSession()
        return client.storage.from("receipts")
            .createSignedUrl(path = path, expiresIn = 5.minutes)
    }

    suspend fun verifyPayment(orderId: String) {
        requireDeveloperSession()
        client.postgrest.rpc(
            "verify_payment",
            buildJsonObject { put("p_order_id", orderId) }
        )
    }

    suspend fun rejectPayment(orderId: String, reason: String) {
        requireDeveloperSession()
        require(reason.trim().length >= 3) { "Enter a rejection reason." }
        client.postgrest.rpc(
            "reject_payment",
            buildJsonObject {
                put("p_order_id", orderId)
                put("p_reason", reason.trim())
            }
        )
    }

    suspend fun collectCash(orderId: String) {
        requireDeveloperSession()
        client.postgrest.rpc(
            "collect_cash",
            buildJsonObject { put("p_order_id", orderId) }
        )
    }

    suspend fun cancelOrder(orderId: String, reason: String) {
        requireDeveloperSession()
        require(reason.trim().length >= 3) { "Enter a cancellation reason." }
        client.postgrest.rpc(
            "cancel_order",
            buildJsonObject {
                put("p_order_id", orderId)
                put("p_reason", reason.trim())
            }
        )
    }

    suspend fun updateOrderStatus(orderId: String, status: String) {
        requireDeveloperSession()
        client.postgrest.rpc(
            "update_order_status",
            buildJsonObject {
                put("p_order_id", orderId)
                put("p_new_status", status)
                put("p_note", "Updated from CBMS Admin Android")
            }
        )
    }

    internal fun realtimeClient(): SupabaseClient = client

    internal suspend fun verifyDeveloperSession() {
        requireDeveloperSession()
    }

    private suspend fun requireDeveloperSession() {
        val user = client.auth.currentUserOrNull()
            ?: throw SecurityException("Your session has expired. Sign in again.")

        if (!user.email.equals(DEVELOPER_EMAIL, ignoreCase = true)) {
            client.auth.signOut()
            throw SecurityException("This account is not approved for CBMS Admin.")
        }

        requireDeveloperProfile()
    }

    private suspend fun requireDeveloperProfile(): DeveloperProfile {
        val user = client.auth.currentUserOrNull()
            ?: throw SecurityException("No authenticated Supabase user.")

        if (!user.email.equals(DEVELOPER_EMAIL, ignoreCase = true)) {
            client.auth.signOut()
            throw SecurityException("This account is not approved for CBMS Admin.")
        }

        val profile = client.from("profiles")
            .select(columns = Columns.list("id", "full_name", "role", "is_active")) {
                filter {
                    eq("id", user.id)
                }
            }
            .decodeSingle<DeveloperProfile>()

        if (!profile.isActive || profile.role != "OWNER" || profile.id != user.id) {
            client.auth.signOut()
            throw SecurityException("Developer access has not been enabled for this account.")
        }

        return profile
    }
}
