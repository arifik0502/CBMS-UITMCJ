package com.crispyburger.admin.ui.home

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crispyburger.admin.data.AdminOrder
import com.crispyburger.admin.data.AdminOrderCounts
import com.crispyburger.admin.data.Formats
import com.crispyburger.admin.data.Money
import com.crispyburger.admin.data.OrderFilter
import com.crispyburger.admin.data.OrderFilters
import com.crispyburger.admin.data.OrderStatus
import com.crispyburger.admin.data.PaymentMethod
import com.crispyburger.admin.data.PaymentStatus
import com.crispyburger.admin.ui.common.MessageCard
import com.crispyburger.admin.ui.common.StatusChip
import com.crispyburger.admin.ui.common.orderTone
import com.crispyburger.admin.ui.common.paymentTone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersScreen(vm: OrdersViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(Unit) { vm.ensureLoaded() }

    val filtered = remember(state.orders, state.search, state.filter) {
        OrderFilters.apply(state.orders, state.search, state.filter)
    }
    val groups = remember(filtered, state.filter) {
        if (state.filter == OrderFilter.TRIPS) OrderFilters.groupByTrip(filtered) else emptyList()
    }
    val fallbackCounts = remember(state.orders) { AdminOrderCounts.fromOrders(state.orders) }
    val counts = state.counts ?: fallbackCounts

    PullToRefreshBox(
        isRefreshing = state.isLoading,
        onRefresh = { vm.refresh() },
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "overview") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        if (state.counts != null) "Overview · all orders" else "Overview · loaded orders only",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SummaryCard(Modifier.weight(1f), "Payment review", counts.paymentReview.toString())
                        SummaryCard(Modifier.weight(1f), "New orders", counts.newOrders.toString())
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SummaryCard(Modifier.weight(1f), "In progress", counts.inProgress.toString())
                        SummaryCard(Modifier.weight(1f), "Delivered today", counts.deliveredToday.toString())
                    }
                }
            }

            item(key = "controls") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Order management", style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(
                        value = state.search,
                        onValueChange = vm::setSearch,
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
                        OrderFilter.entries.forEach { filter ->
                            FilterChip(
                                selected = state.filter == filter,
                                onClick = { vm.setFilter(filter) },
                                label = { Text(filter.label) }
                            )
                        }
                    }
                }
            }

            state.info?.let { info -> item(key = "info") { MessageCard(info, isError = false) } }
            state.error?.let { error -> item(key = "error") { MessageCard(error, isError = true) } }

            if (filtered.isEmpty() && !state.isLoading) {
                item(key = "empty") {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            if (state.orders.isEmpty()) "No orders have been submitted yet."
                            else "No orders match this search or filter.",
                            modifier = Modifier.padding(18.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (state.filter == OrderFilter.TRIPS) {
                groups.forEach { group ->
                    item(key = "group-${group.key}") {
                        Text(
                            group.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    items(group.orders, key = { it.id }) { order ->
                        OrderCard(order) { vm.select(order.id) }
                    }
                }
            } else {
                items(filtered, key = { it.id }) { order ->
                    OrderCard(order) { vm.select(order.id) }
                }
            }

            if (state.canLoadMore) {
                item(key = "more") {
                    OutlinedButton(onClick = vm::loadMore, modifier = Modifier.fillMaxWidth()) {
                        Text("Load older orders")
                    }
                }
            }

            item(key = "footer") {
                Text(
                    "Operational changes are sent to Supabase RPC functions. The app does not write order or payment tables directly.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    state.selectedOrder?.let { order ->
        OrderDetailDialog(
            order = order,
            state = state,
            onClose = vm::dismissDetail,
            onAction = vm::perform,
            onViewReceipt = vm::viewReceipt
        )
    }

    if (state.receipt !is ReceiptState.None) {
        ReceiptViewerDialog(
            receipt = state.receipt,
            onClose = vm::dismissReceipt,
            onOpenBrowser = { url ->
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            }
        )
    }
}

@Composable
private fun SummaryCard(modifier: Modifier, title: String, value: String) {
    Card(modifier = modifier, shape = RoundedCornerShape(16.dp)) {
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
private fun OrderCard(order: AdminOrder, onClick: () -> Unit) {
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
                Text(Money.format(order.totalCents), fontWeight = FontWeight.Bold)
            }
            Text(order.customerName, style = MaterialTheme.typography.bodyLarge)
            Text(
                order.deliveryAddress,
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
                StatusChip(OrderStatus.label(order.orderStatus), orderTone(order.orderStatus))
                StatusChip(
                    "${PaymentMethod.label(order.paymentMethod)} · ${PaymentStatus.label(order.paymentStatus)}",
                    paymentTone(order.paymentStatus)
                )
            }
            Text(
                "Ordered ${Formats.malaysiaDateTime(order.createdAt)} · tap to review",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}
