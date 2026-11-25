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
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import java.time.Instant
import java.time.LocalDate
import kotlin.time.Duration.Companion.minutes
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
private data class AdminCashCollectionRow(
    @SerialName("order_id") val orderId: String,
    @SerialName("amount_cents") val amountCents: Long,
    @SerialName("collected_at") val collectedAt: String
)

@Serializable
private data class AdminCashCollectedOrder(
    val id: String,
    @SerialName("order_no") val orderNo: String
)

@Serializable
private data class AdminDeviceTokenUpsert(
    @SerialName("user_id") val userId: String,
    val token: String,
    val platform: String = "android",
    @SerialName("last_seen_at") val lastSeenAt: String
)

/** Result of the get_sales_summary RPC (see supabase/migrations/20261119000003_admin_reporting_rpcs.sql). */
@Serializable
private data class AdminSalesSummaryDto(
    @SerialName("delivered_count") val deliveredCount: Int = 0,
    @SerialName("delivered_cents") val deliveredCents: Long = 0,
    @SerialName("settled_count") val settledCount: Int = 0,
    @SerialName("settled_cents") val settledCents: Long = 0,
    @SerialName("cash_count") val cashCount: Int = 0,
    @SerialName("cash_cents") val cashCents: Long = 0
)

@Serializable
private data class AdminCashCollectionDto(
    @SerialName("order_no") val orderNo: String,
    @SerialName("amount_cents") val amountCents: Long,
    @SerialName("collected_at") val collectedAt: String
)

object AdminSupabase : AdminApi {
    private const val PROFILE_TTL_MS = 5 * 60 * 1000L
    private const val LOOKUP_TTL_MS = 10 * 60 * 1000L
    private const val PAGE_SIZE = 1000L
    private const val MAX_EXPORT_ROWS = 10_000
    private const val RECENT_LIST_SIZE = 20

    private val ORDER_COLUMNS = Columns.list(
        "id", "order_no", "customer_name", "customer_phone", "building_id", "floor_number",
        "room_number", "payment_method", "order_status", "payment_status", "total_cents",
        "created_at", "delivered_at", "trip_id", "trip_date", "note", "customer_edit_revision", "cancelled_reason"
    )

    private val SALES_COLUMNS = Columns.list(
        "id", "order_no", "payment_status", "payment_method", "total_cents", "delivered_at"
    )

    val isConfigured: Boolean
        get() = BuildConfig.SUPABASE_URL.isNotBlank() &&
            BuildConfig.SUPABASE_ANON_KEY.isNotBlank()

    /** Optional sign-in email prefill from local.properties. Server roles remain the authority. */
    val configuredEmail: String
        get() = BuildConfig.DEVELOPER_EMAIL.trim()

    override fun friendlyError(error: Throwable, fallback: String): String =
        ErrorMessages.map(error, fallback)

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
            install(Storage)
        }
    }

    // ---- session / profile (profile is cached so each call is not an extra round trip) ----

    @Volatile private var cachedProfile: DeveloperProfile? = null
    @Volatile private var cachedProfileAt = 0L

    private class Lookups(
        val buildings: Map<String, AdminBuilding>,
        val trips: Map<String, AdminDeliveryTrip>,
        val loadedAt: Long
    )

    @Volatile private var lookups: Lookups? = null

    private fun clearCaches() {
        cachedProfile = null
        cachedProfileAt = 0L
        lookups = null
    }

    suspend fun signInDeveloper(email: String, password: String): DeveloperProfile {
        require(email.isNotBlank()) { "Enter your email." }
        require(password.isNotBlank()) { "Enter your password." }
        client.auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }

        return requireDeveloperProfile(force = true)
    }

    suspend fun restoreDeveloperSession(): DeveloperProfile? {
        if (!isConfigured) return null

        client.auth.awaitInitialization()
        client.auth.currentUserOrNull() ?: return null

        return try {
            requireDeveloperProfile(force = true)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
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
        clearCaches()
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

    // ---- orders ----

    override suspend fun loadRecentOrders(maxRows: Int): List<AdminOrder> {
        requireDeveloperSession()

        val orders = client.from("orders")
            .select(columns = ORDER_COLUMNS) {
                order("created_at", Order.DESCENDING)
                limit(maxRows.toLong())
            }
            .decodeList<AdminOrder>()

        var lk = loadLookups()
        val stale = orders.any { o ->
            o.buildingId !in lk.buildings || (o.tripId != null && o.tripId !in lk.trips)
        }
        if (stale) lk = loadLookups(force = true)

        return orders.map { order ->
            val building = lk.buildings[order.buildingId]
            val trip = order.tripId?.let { lk.trips[it] }
            order.copy(
                buildingLabel = if (building == null) {
                    "Unknown building"
                } else {
                    "${building.code} - ${building.name}"
                },
                tripLabel = trip?.label ?: order.tripId?.let { "Trip unavailable" } ?: "No trip assigned",
                tripWindow = trip?.let {
                    "${Formats.tripTime(it.startTime)} – ${Formats.tripTime(it.endTime)}"
                } ?: "",
                tripNo = trip?.tripNo ?: 0
            )
        }
    }

    private suspend fun loadLookups(force: Boolean = false): Lookups {
        val cached = lookups
        val now = System.currentTimeMillis()
        if (!force && cached != null && now - cached.loadedAt < LOOKUP_TTL_MS) return cached

        val buildings = client.from("buildings")
            .select(columns = Columns.list("id", "code", "name"))
            .decodeList<AdminBuilding>()
            .associateBy { it.id }

        val trips = client.from("delivery_trips")
            .select(columns = Columns.list("id", "trip_no", "label", "start_time", "end_time"))
            .decodeList<AdminDeliveryTrip>()
            .associateBy { it.id }

        return Lookups(buildings, trips, now).also { lookups = it }
    }

    /** Whole-table counts from the server; null when the RPC is not installed yet. */
    override suspend fun loadOrderCounts(): AdminOrderCounts? {
        requireDeveloperSession()
        return runCatchingCancellable {
            client.postgrest.rpc("get_order_counts", buildJsonObject { }).decodeAs<AdminOrderCounts>()
        }.getOrNull()
    }

    /** Order lines; null when the items table/columns differ from the expected schema. */
    override suspend fun loadOrderItems(orderId: String): List<AdminOrderItem>? {
        requireDeveloperSession()
        val attempts = listOf(
            "quantity,unit_price_cents,line_total_cents,products(name)",
            "quantity,unit_price_cents,products(name)",
            "quantity,products(name)"
        )
        for (columns in attempts) {
            val result = runCatchingCancellable {
                client.from("order_items")
                    .select(columns = Columns.raw(columns)) {
                        filter { eq("order_id", orderId) }
                    }
                    .decodeList<AdminOrderItem>()
            }
            if (result.isSuccess) return result.getOrThrow()
        }
        return null
    }

    override suspend fun loadPayment(orderId: String): AdminPayment? {
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

    override suspend fun createReceiptUrl(path: String): String {
        requireDeveloperSession()
        return client.storage.from("receipts")
            .createSignedUrl(path = path, expiresIn = 5.minutes)
    }

    override suspend fun verifyPayment(orderId: String) {
        requireDeveloperSession()
        client.postgrest.rpc(
            "verify_payment",
            buildJsonObject { put("p_order_id", orderId) }
        )
    }

    override suspend fun rejectPayment(orderId: String, reason: String) {
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

    override suspend fun collectCash(orderId: String) {
        requireDeveloperSession()
        client.postgrest.rpc(
            "collect_cash",
            buildJsonObject { put("p_order_id", orderId) }
        )
    }

    override suspend fun cancelOrder(orderId: String, reason: String) {
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

    override suspend fun updateOrderStatus(orderId: String, status: String) {
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

    // ---- support tickets ----

    override suspend fun loadTickets(maxRows: Int): List<AdminTicket> {
        requireDeveloperSession()
        return client.from("support_tickets")
            .select(
                columns = Columns.raw(
                    "id,ticket_no,message,customer_name,customer_phone,attachment_path," +
                        "status,created_at,resolved_at,orders(order_no)"
                )
            ) {
                order("created_at", Order.DESCENDING)
                limit(maxRows.toLong())
            }
            .decodeList<AdminTicket>()
    }

    override suspend fun setTicketStatus(ticketId: String, status: String) {
        requireDeveloperSession()
        client.postgrest.rpc(
            "set_support_ticket_status",
            buildJsonObject {
                put("p_ticket_id", ticketId)
                put("p_status", status)
            }
        )
    }

    override suspend fun createTicketPhotoUrl(path: String): String {
        requireDeveloperSession()
        return client.storage.from("ticket-attachments")
            .createSignedUrl(path = path, expiresIn = 5.minutes)
    }

    // ---- sales report ----

    override suspend fun loadSalesReport(from: LocalDate, to: LocalDate): AdminSalesReport {
        requireDeveloperSession()
        ReportRange.validate(from, to)?.let { throw IllegalArgumentException(it) }
        val (fromInstant, toExclusive) = ReportRange.bounds(from, to)

        val serverResult = runCatchingCancellable {
            val summary = client.postgrest.rpc(
                "get_sales_summary",
                buildJsonObject {
                    put("p_from", fromInstant)
                    put("p_to", toExclusive)
                }
            ).decodeAs<AdminSalesSummaryDto>()

            val cash = client.postgrest.rpc(
                "get_cash_collections",
                buildJsonObject {
                    put("p_from", fromInstant)
                    put("p_to", toExclusive)
                    put("p_limit", RECENT_LIST_SIZE)
                }
            ).decodeList<AdminCashCollectionDto>()

            summary to cash
        }

        if (serverResult.isSuccess) {
            val (summary, cash) = serverResult.getOrThrow()
            return AdminSalesReport(
                summary = SalesReportSummary(
                    deliveredOrderCount = summary.deliveredCount,
                    settledOrderCount = summary.settledCount,
                    outstandingOrderCount = summary.deliveredCount - summary.settledCount,
                    deliveredValueCents = summary.deliveredCents,
                    settledRevenueCents = summary.settledCents,
                    outstandingValueCents = summary.deliveredCents - summary.settledCents
                ),
                recentOrders = loadDeliveredPage(fromInstant, toExclusive, 0L, RECENT_LIST_SIZE.toLong()),
                cashCollections = cash.map {
                    AdminCashCollection(it.orderNo, it.amountCents, it.collectedAt)
                },
                cashCollectedCents = summary.cashCents,
                cashCollectionCount = summary.cashCount,
                approximate = false
            )
        }

        // Fallback when the migration has not been applied: page through everything (capped).
        val all = loadAllDeliveredOrders(from, to)
        val cash = loadCashCollectionsFallback(fromInstant, toExclusive)
        return AdminSalesReport(
            summary = SalesReportCalculator.calculate(all),
            recentOrders = all.take(RECENT_LIST_SIZE),
            cashCollections = cash.take(RECENT_LIST_SIZE),
            cashCollectedCents = cash.sumOf { it.amountCents },
            cashCollectionCount = cash.size,
            approximate = all.size >= MAX_EXPORT_ROWS
        )
    }

    override suspend fun loadAllDeliveredOrders(from: LocalDate, to: LocalDate): List<AdminSalesOrder> {
        requireDeveloperSession()
        ReportRange.validate(from, to)?.let { throw IllegalArgumentException(it) }
        val (fromInstant, toExclusive) = ReportRange.bounds(from, to)

        val all = mutableListOf<AdminSalesOrder>()
        var offset = 0L
        while (all.size < MAX_EXPORT_ROWS) {
            val page = loadDeliveredPage(fromInstant, toExclusive, offset, PAGE_SIZE)
            all += page
            if (page.size < PAGE_SIZE) break
            offset += PAGE_SIZE
        }
        return all
    }

    private suspend fun loadDeliveredPage(
        fromInstant: String,
        toExclusive: String,
        offset: Long,
        count: Long
    ): List<AdminSalesOrder> =
        client.from("orders")
            .select(columns = SALES_COLUMNS) {
                filter {
                    gte("delivered_at", fromInstant)
                    lt("delivered_at", toExclusive)
                }
                order("delivered_at", Order.DESCENDING)
                order("id", Order.ASCENDING)
                range(offset, offset + count - 1)
            }
            .decodeList<AdminSalesOrder>()

    private suspend fun loadCashCollectionsFallback(
        fromInstant: String,
        toExclusive: String
    ): List<AdminCashCollection> {
        val payments = client.from("payments")
            .select(columns = Columns.list("order_id", "amount_cents", "collected_at")) {
                filter {
                    gte("collected_at", fromInstant)
                    lt("collected_at", toExclusive)
                }
                order("collected_at", Order.DESCENDING)
                range(0L, PAGE_SIZE - 1)
            }
            .decodeList<AdminCashCollectionRow>()

        if (payments.isEmpty()) return emptyList()

        val collected = mutableMapOf<String, AdminCashCollectedOrder>()
        payments.map { it.orderId }.distinct().chunked(100).forEach { ids ->
            client.from("orders")
                .select(columns = Columns.list("id", "order_no")) {
                    filter {
                        eq("payment_status", PaymentStatus.CASH_COLLECTED)
                        isIn("id", ids)
                    }
                }
                .decodeList<AdminCashCollectedOrder>()
                .forEach { collected[it.id] = it }
        }

        return payments.mapNotNull { payment ->
            val order = collected[payment.orderId] ?: return@mapNotNull null
            AdminCashCollection(order.orderNo, payment.amountCents, payment.collectedAt)
        }
    }

    // ---- website customization ----

    override suspend fun loadSiteState(): SiteState {
        requireDeveloperSession()
        val json = client.postgrest.rpc("admin_get_site_state", buildJsonObject { }).data
        return SiteJson.decodeFromString(SiteState.serializer(), json)
    }

    override suspend fun saveSiteDraft(config: SiteConfig) {
        requireDeveloperSession()
        client.postgrest.rpc(
            "admin_save_site_draft",
            buildJsonObject { put("p_config", SiteJson.encodeToJsonElement(SiteConfig.serializer(), config)) }
        )
    }

    override suspend fun publishSite() {
        requireDeveloperSession()
        client.postgrest.rpc("admin_publish_site", buildJsonObject { })
    }

    override suspend fun discardSiteDraft() {
        requireDeveloperSession()
        client.postgrest.rpc("admin_discard_site_draft", buildJsonObject { })
    }

    override suspend fun loadSiteHistory(): List<SiteVersion> {
        requireDeveloperSession()
        val json = client.postgrest.rpc("admin_list_site_history", buildJsonObject { }).data
        return SiteJson.decodeFromString(ListSerializer(SiteVersion.serializer()), json)
    }

    override suspend fun restoreSiteVersion(id: String) {
        requireDeveloperSession()
        client.postgrest.rpc("admin_restore_site_version", buildJsonObject { put("p_id", id) })
    }

    override suspend fun uploadSiteImage(jpeg: ByteArray): String {
        requireDeveloperSession()
        require(jpeg.isNotEmpty()) { "The picture is empty." }
        require(jpeg.size <= 1_000_000) { "The picture is too big. Pick a smaller one." }
        val suffix = java.util.UUID.randomUUID().toString().replace("-", "").take(8)
        val path = "${System.currentTimeMillis()}-$suffix.jpg"
        val bucket = client.storage.from("site-assets")
        bucket.upload(path, jpeg) {
            upsert = false
            contentType = io.ktor.http.ContentType.Image.JPEG
        }
        return bucket.publicUrl(path)
    }

    // ---- catalog / inventory ----

    override suspend fun loadProducts(): List<AdminProduct> {
        requireDeveloperSession()
        return client.postgrest.rpc(
            "admin_list_products",
            buildJsonObject { }
        ).decodeAs()
    }

    override suspend fun createProduct(
        sku: String,
        name: String,
        description: String,
        priceCents: Int,
        imageUrl: String?,
        initialStock: Int
    ) {
        requireDeveloperSession()
        val normalizedSku = ProductValidation.normalizeSku(sku)
            ?: throw IllegalArgumentException("SKU must be 2-20 letters, numbers, hyphens or underscores.")
        require(name.trim().length in 2..80) { "Product name must be 2-80 characters." }
        require(description.trim().length <= 500) { "Description must be 500 characters or fewer." }
        require(priceCents in 1..1_000_000) { "Enter a valid price." }
        require(initialStock in 0..100_000) { "Initial stock must be between 0 and 100,000." }

        client.postgrest.rpc(
            "admin_create_product",
            buildJsonObject {
                put("p_sku", normalizedSku)
                put("p_name", name.trim())
                put("p_description", description.trim())
                put("p_price_cents", priceCents)
                put("p_image_url", imageUrl)
                put("p_initial_stock", initialStock)
            }
        )
    }

    override suspend fun updateProductDetails(
        productId: String,
        sku: String,
        name: String,
        description: String,
        priceCents: Int,
        imageUrl: String?,
        isActive: Boolean
    ) {
        requireDeveloperSession()
        val normalizedSku = ProductValidation.normalizeSku(sku)
            ?: throw IllegalArgumentException("SKU must be 2-20 letters, numbers, hyphens or underscores.")
        require(name.trim().length in 2..80) { "Product name must be 2-80 characters." }
        require(description.trim().length <= 500) { "Description must be 500 characters or fewer." }
        require(priceCents in 1..1_000_000) { "Enter a valid price." }

        client.postgrest.rpc(
            "admin_update_product",
            buildJsonObject {
                put("p_product_id", productId)
                put("p_sku", normalizedSku)
                put("p_name", name.trim())
                put("p_description", description.trim())
                put("p_price_cents", priceCents)
                put("p_image_url", imageUrl)
                put("p_is_active", isActive)
            }
        )
    }

    override suspend fun uploadProductImage(jpeg: ByteArray): String {
        requireDeveloperSession()
        require(jpeg.isNotEmpty()) { "The product photo is empty." }
        require(jpeg.size <= 1_000_000) { "The product photo is too big. Pick a smaller one." }
        val suffix = java.util.UUID.randomUUID().toString().replace("-", "").take(8)
        val path = "${System.currentTimeMillis()}-$suffix.jpg"
        val bucket = client.storage.from("product-images")
        bucket.upload(path, jpeg) {
            upsert = false
            contentType = io.ktor.http.ContentType.Image.JPEG
        }
        return bucket.publicUrl(path)
    }

    override suspend fun loadDeliverySettings(): AdminDeliverySettings {
        requireDeveloperSession()
        return client.postgrest.rpc(
            "admin_get_delivery_settings",
            buildJsonObject { }
        ).decodeAs<AdminDeliverySettings>()
    }

    override suspend fun saveDeliveryTrip(
        id: String?,
        tripNo: Int,
        label: String,
        startTime: String,
        endTime: String,
        isOpen: Boolean
    ) {
        requireDeveloperSession()
        require(tripNo in 1..50) { "Trip number must be between 1 and 50." }
        require(label.trim().isNotEmpty()) { "Enter a trip name." }
        client.postgrest.rpc(
            "admin_save_trip",
            buildJsonObject {
                put("p_id", id)
                put("p_trip_no", tripNo)
                put("p_label", label.trim())
                put("p_start_time", startTime)
                put("p_end_time", endTime)
                put("p_is_open", isOpen)
            }
        )
        lookups = null
    }

    override suspend fun saveBuilding(
        id: String?,
        code: String,
        name: String,
        floors: List<Int>,
        sortOrder: Int,
        isActive: Boolean
    ) {
        requireDeveloperSession()
        val normalizedCode = code.trim().uppercase()
        require(normalizedCode.matches(Regex("^[A-Z0-9][A-Z0-9_-]{0,7}$"))) {
            "Building code must be 1-8 letters or numbers."
        }
        require(name.trim().isNotEmpty()) { "Enter a building name." }
        require(floors.isNotEmpty()) { "Add at least one floor." }
        client.postgrest.rpc(
            "admin_save_building",
            buildJsonObject {
                put("p_id", id)
                put("p_code", normalizedCode)
                put("p_name", name.trim())
                put("p_floors", buildJsonArray { floors.distinct().sorted().forEach { add(it) } })
                put("p_sort_order", sortOrder)
                put("p_is_active", isActive)
            }
        )
        lookups = null
    }

    override suspend fun saveOrderControls(acceptingOrders: Boolean, maxQtyPerOrder: Int) {
        requireDeveloperSession()
        require(maxQtyPerOrder in 1..99) { "Maximum items per order must be between 1 and 99." }
        client.postgrest.rpc(
            "admin_set_order_controls",
            buildJsonObject {
                put("p_accepting_orders", acceptingOrders)
                put("p_max_qty_per_order", maxQtyPerOrder)
            }
        )
    }

    override suspend fun loadInventory(): List<AdminInventoryItem> {
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

    override suspend fun adjustInventory(
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

    internal fun realtimeClient(): SupabaseClient = client

    internal suspend fun verifyDeveloperSession() {
        requireDeveloperSession()
    }

    private suspend fun requireDeveloperSession() {
        requireDeveloperProfile(force = false)
    }

    private suspend fun requireDeveloperProfile(force: Boolean): DeveloperProfile {
        val user = client.auth.currentUserOrNull()
            ?: throw SecurityException("Your session has expired. Sign in again.")

        val cached = cachedProfile
        val now = System.currentTimeMillis()
        if (!force && cached != null && cached.id == user.id && now - cachedProfileAt < PROFILE_TTL_MS) {
            return cached
        }

        val profile = client.from("profiles")
            .select(columns = Columns.list("id", "full_name", "role", "is_active")) {
                filter {
                    eq("id", user.id)
                }
            }
            .decodeSingle<DeveloperProfile>()

        if (!profile.isActive || profile.role !in setOf("OWNER", "ADMIN") || profile.id != user.id) {
            clearCaches()
            client.auth.signOut()
            throw SecurityException("Admin access has not been enabled for this account.")
        }

        cachedProfile = profile
        cachedProfileAt = now
        return profile
    }
}
