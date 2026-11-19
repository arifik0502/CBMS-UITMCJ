package com.crispyburger.admin.ui.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.crispyburger.admin.data.AdminOrder
import com.crispyburger.admin.data.AdminPayment
import com.crispyburger.admin.data.AdminRealtimeManager
import com.crispyburger.admin.data.AdminSupabase
import com.crispyburger.admin.data.DeveloperProfile
import com.crispyburger.admin.notifications.AdminNotificationHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AdminDashboardScreen(
    profile: DeveloperProfile,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var realtimeStatus by remember { mutableStateOf("Connecting") }
    val realtimeRefreshKey by AdminRealtimeManager.dataChanges.collectAsState()
    var reportRefreshKey by remember { mutableStateOf(0) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    var orders by remember { mutableStateOf(emptyList<AdminOrder>()) }
    var isLoading by remember { mutableStateOf(true) }
    var isWorking by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var infoMessage by remember { mutableStateOf<String?>(null) }
    var searchText by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("ACTIVE") }
    var selectedOrder by remember { mutableStateOf<AdminOrder?>(null) }
    var paymentInfo by remember { mutableStateOf<AdminPayment?>(null) }
    var paymentLoading by remember { mutableStateOf(false) }
    var reasonDialog by remember { mutableStateOf<String?>(null) }
    var reasonText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        AdminNotificationHelper.createChannel(context.applicationContext)
        if (
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            try {
                AdminRealtimeManager.start(context.applicationContext)
                realtimeStatus = "Connected"
                awaitCancellation()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                realtimeStatus = "Unavailable — use Refresh"
            } finally {
                withContext(NonCancellable) {
                    runCatching { AdminRealtimeManager.stop() }
                }
            }
        }
    }

    fun refresh() {
        scope.launch {
            isLoading = true
            errorMessage = null
            try {
                orders = AdminSupabase.loadRecentOrders()
            } catch (error: Exception) {
                errorMessage = AdminSupabase.friendlyError(error, "Could not load orders. Check your connection and database setup.")
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(realtimeRefreshKey) {
        if (realtimeRefreshKey > 0) refresh()
    }

    fun performAction(action: suspend () -> Unit) {
        scope.launch {
            isWorking = true
            errorMessage = null
            infoMessage = null
            try {
                action()
                selectedOrder = null
                paymentInfo = null
                infoMessage = "Order updated successfully."
                orders = AdminSupabase.loadRecentOrders()
                reportRefreshKey += 1
            } catch (error: Exception) {
                errorMessage = AdminSupabase.friendlyError(error, "The action could not be completed. Check your connection and access permissions.")
            } finally {
                isWorking = false
            }
        }
    }

    LaunchedEffect(Unit) {
        refresh()
    }

    LaunchedEffect(selectedOrder?.id) {
        val orderId = selectedOrder?.id
        paymentInfo = null
        if (orderId != null) {
            paymentLoading = true
            try {
                paymentInfo = AdminSupabase.loadPayment(orderId)
            } catch (error: Exception) {
                errorMessage = AdminSupabase.friendlyError(error, "Could not load payment details.")
            } finally {
                paymentLoading = false
            }
        }
    }

    val filteredOrders = orders.filter { order ->
        val matchesSearch = searchText.isBlank() ||
            order.orderNo.contains(searchText, ignoreCase = true) ||
            order.customerName.contains(searchText, ignoreCase = true) ||
            order.customerPhone.contains(searchText, ignoreCase = true)

        val matchesStatus = when (statusFilter) {
            "PAYMENT" -> order.paymentStatus == "PENDING_VERIFICATION"
            "ACTIVE" -> order.orderStatus !in listOf("DELIVERED", "CANCELLED")
            "ARCHIVE" -> order.orderStatus in listOf("DELIVERED", "CANCELLED")
            else -> true
        }
        matchesSearch && matchesStatus
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("CBMS ADMIN", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Welcome, ${profile.fullName.ifBlank { "Developer" }}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            TextButton(onClick = onSignOut) {
                Text("Sign out")
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text("Developer access active", fontWeight = FontWeight.SemiBold)
                Text(
                    "Only the approved developer account can use this admin workspace. Order preparation and delivery milestones are managed here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Live updates: $realtimeStatus",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (realtimeStatus == "Connected") {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.secondary
                    }
                )
            }
        }

        Text("Overview · latest 100 orders", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryCard(
                modifier = Modifier.weight(1f),
                title = "Payment review",
                value = orders.count { it.paymentStatus == "PENDING_VERIFICATION" }.toString()
            )
            SummaryCard(
                modifier = Modifier.weight(1f),
                title = "New orders",
                value = orders.count { it.orderStatus == "NEW" || it.orderStatus == "CONFIRMED" }.toString()
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryCard(
                modifier = Modifier.weight(1f),
                title = "In progress",
                value = orders.count { it.orderStatus in listOf("PREPARING", "READY", "ASSIGNED", "OUT_FOR_DELIVERY") }.toString()
            )
            SummaryCard(
                modifier = Modifier.weight(1f),
                title = "Delivered",
                value = orders.count { it.orderStatus == "DELIVERED" }.toString()
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Order management", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = { refresh() }, enabled = !isLoading && !isWorking) {
                Text("Refresh")
            }
        }

        OutlinedTextField(
            value = searchText,
            onValueChange = { searchText = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search order, customer or phone") },
            singleLine = true
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("ACTIVE", "PAYMENT", "ARCHIVE").forEach { filter ->
                FilterChip(
                    selected = statusFilter == filter,
                    onClick = { statusFilter = filter },
                    label = { Text(filterLabel(filter)) }
                )
            }
        }

        if (infoMessage != null) {
            Text(infoMessage!!, color = MaterialTheme.colorScheme.primary)
        }
        if (errorMessage != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    errorMessage!!,
                    modifier = Modifier.padding(14.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        when {
            isLoading -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            filteredOrders.isEmpty() -> {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        if (orders.isEmpty()) "No orders have been submitted yet."
                        else "No orders match this search or filter.",
                        modifier = Modifier.padding(18.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> {
                filteredOrders.forEach { order ->
                    OrderCard(order = order, onClick = { selectedOrder = order })
                }
            }
        }

        Text(
            "Operational changes are sent to Supabase RPC functions. The app does not write order or payment tables directly.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        ProductCatalogSection(refreshKey = realtimeRefreshKey)

        InventorySection(refreshKey = realtimeRefreshKey)

        SalesReportSection(refreshKey = realtimeRefreshKey + reportRefreshKey)

        Spacer(modifier = Modifier.height(10.dp))
    }

    val currentSelectedOrder = selectedOrder?.let { selected ->
        orders.firstOrNull { it.id == selected.id } ?: selected
    }

    if (currentSelectedOrder != null) {
        val order = currentSelectedOrder
        AlertDialog(
            onDismissRequest = { if (!isWorking) selectedOrder = null },
            title = { Text(order.orderNo) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    DetailLine("Customer", order.customerName)
                    DetailLine("Phone", order.customerPhone)
                    DetailLine("Location", "${order.buildingLabel}, Floor ${order.floorNumber}, Room ${order.roomNumber}")
                    DetailLine("Order status", statusLabel(order.orderStatus))
                    DetailLine("Payment", "${order.paymentMethod} · ${statusLabel(order.paymentStatus)}")
                    DetailLine("Total", formatRinggit(order.totalCents))
                    DetailLine("Order date", order.createdAt.take(10))
                    DetailLine("Delivery trip", order.tripLabel)
                    if (order.tripWindow.isNotBlank()) DetailLine("Trip window", order.tripWindow)
                    if (!order.tripDate.isNullOrBlank()) DetailLine("Trip date", order.tripDate!!)
                    if (!order.note.isNullOrBlank()) DetailLine("Customer note", order.note!!)
                    if (paymentLoading) {
                        CircularProgressIndicator()
                    } else if (paymentInfo?.receiptPath != null) {
                        OutlinedButton(
                            onClick = {
                                val receiptPath = paymentInfo?.receiptPath
                                if (receiptPath != null) {
                                    scope.launch {
                                        isWorking = true
                                        errorMessage = null
                                        try {
                                            val url = AdminSupabase.createReceiptUrl(receiptPath)
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                        } catch (error: Exception) {
                                            errorMessage = AdminSupabase.friendlyError(error, "Could not open the private receipt.")
                                        } finally {
                                            isWorking = false
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isWorking
                        ) {
                            Text("Open payment receipt")
                        }
                    } else if (order.paymentStatus == "PENDING_VERIFICATION") {
                        Text(
                            "No receipt path was returned. Check the payment row and private receipts bucket permissions.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    if (order.paymentStatus == "PENDING_VERIFICATION") {
                        Button(
                            onClick = { performAction { AdminSupabase.verifyPayment(order.id) } },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isWorking && paymentInfo?.receiptPath != null
                        ) {
                            Text("Verify payment")
                        }
                        OutlinedButton(
                            onClick = {
                                reasonText = ""
                                reasonDialog = "REJECT"
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isWorking
                        ) {
                            Text("Reject payment")
                        }
                    }

                    if (order.orderStatus in listOf("OUT_FOR_DELIVERY", "DELIVERED") && order.paymentStatus == "CASH_PENDING") {
                        Button(
                            onClick = { performAction { AdminSupabase.collectCash(order.id) } },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isWorking
                        ) {
                            Text("Confirm cash collected")
                        }
                    }

                    val nextStatus = nextOfficeStatus(order.orderStatus)
                    if (nextStatus != null) {
                        OutlinedButton(
                            onClick = {
                                performAction { AdminSupabase.updateOrderStatus(order.id, nextStatus) }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isWorking && !(
                                nextStatus == "DELIVERED" &&
                                    order.paymentMethod == "DUITNOW" &&
                                    order.paymentStatus != "VERIFIED"
                                )
                        ) {
                            Text(
                                when (nextStatus) {
                                    "OUT_FOR_DELIVERY" -> "Mark out for delivery"
                                    "DELIVERED" -> "Mark as delivered"
                                    else -> "Move to ${statusLabel(nextStatus)}"
                                }
                            )
                        }
                    }

                    if (
                        order.orderStatus == "OUT_FOR_DELIVERY" &&
                        order.paymentMethod == "DUITNOW" &&
                        order.paymentStatus != "VERIFIED"
                    ) {
                        Text(
                            "Verify the DuitNow payment before marking this order as delivered.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    if (order.orderStatus !in listOf("DELIVERED", "CANCELLED")) {
                        OutlinedButton(
                            onClick = {
                                reasonText = ""
                                reasonDialog = "CANCEL"
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isWorking
                        ) {
                            Text(if (order.paymentStatus == "CASH_PENDING") "Cancel cash order" else "Cancel order")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedOrder = null }, enabled = !isWorking) {
                    Text("Close")
                }
            }
        )
    }

    if (reasonDialog != null) {
        val isReject = reasonDialog == "REJECT"
        AlertDialog(
            onDismissRequest = { if (!isWorking) reasonDialog = null },
            title = { Text(if (isReject) "Reject payment" else "Cancel order") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (isReject) "Provide a reason so the customer can understand why the receipt was rejected."
                        else "Provide a reason for cancelling this order."
                    )
                    OutlinedTextField(
                        value = reasonText,
                        onValueChange = { reasonText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Reason") },
                        minLines = 2
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val orderId = selectedOrder?.id
                        val reason = reasonText.trim()
                        if (orderId != null && reason.length >= 3) {
                            reasonDialog = null
                            performAction {
                                if (isReject) AdminSupabase.rejectPayment(orderId, reason)
                                else AdminSupabase.cancelOrder(orderId, reason)
                            }
                        }
                    },
                    enabled = reasonText.trim().length >= 3 && !isWorking
                ) {
                    Text(if (isReject) "Reject payment" else "Cancel order")
                }
            },
            dismissButton = {
                TextButton(onClick = { reasonDialog = null }, enabled = !isWorking) {
                    Text("Back")
                }
            }
        )
    }
}

@Composable
private fun SummaryCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(title, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun OrderCard(
    order: AdminOrder,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(order.orderNo, fontWeight = FontWeight.SemiBold)
                Text(formatRinggit(order.totalCents), fontWeight = FontWeight.Bold)
            }
            Text(order.customerName, style = MaterialTheme.typography.bodyLarge)
            Text(
                "${order.buildingLabel} · Floor ${order.floorNumber} · Room ${order.roomNumber}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                if (order.tripWindow.isNotBlank()) "${order.tripLabel} · ${order.tripWindow}" else order.tripLabel,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    statusLabel(order.orderStatus),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text("·", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    statusLabel(order.paymentStatus),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (order.paymentStatus == "PENDING_VERIFICATION") {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.secondary
                    }
                )
            }
            Text(
                "Tap to review",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
        Text(value, style = MaterialTheme.typography.bodyMedium)
        HorizontalDivider()
    }
}

private fun nextOfficeStatus(status: String): String? {
    return when (status) {
        "NEW" -> "CONFIRMED"
        "CONFIRMED" -> "PREPARING"
        "PREPARING" -> "READY"
        "READY", "ASSIGNED" -> "OUT_FOR_DELIVERY"
        "OUT_FOR_DELIVERY" -> "DELIVERED"
        else -> null
    }
}

private fun statusLabel(value: String): String {
    if (value == "ASSIGNED") return "Ready"
    return value.lowercase()
        .replace('_', ' ')
        .replaceFirstChar { it.uppercase() }
}

private fun filterLabel(value: String): String {
    return when (value) {
        "PAYMENT" -> "Payment review"
        "ACTIVE" -> "Active"
        else -> statusLabel(value)
    }
}

private fun formatRinggit(cents: Int): String {
    val amount = cents.coerceAtLeast(0)
    return "RM ${amount / 100}.${(amount % 100).toString().padStart(2, '0')}"
}
