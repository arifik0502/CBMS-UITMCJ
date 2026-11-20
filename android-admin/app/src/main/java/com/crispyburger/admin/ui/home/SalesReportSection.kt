package com.crispyburger.admin.ui.home

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crispyburger.admin.data.AdminSalesReport
import com.crispyburger.admin.data.Formats
import com.crispyburger.admin.data.Money
import com.crispyburger.admin.data.PaymentMethod
import com.crispyburger.admin.data.PaymentStatus
import com.crispyburger.admin.data.ReportRange
import com.crispyburger.admin.ui.common.ButtonProgress
import com.crispyburger.admin.ui.common.MessageCard
import com.crispyburger.admin.ui.common.SectionHeader
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private enum class PickerTarget { FROM, TO }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(vm: ReportViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var picker by remember { mutableStateOf<PickerTarget?>(null) }
    LaunchedEffect(Unit) { vm.ensureLoaded() }

    LaunchedEffect(Unit) {
        vm.csv.collect { csv ->
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "CBMS sales report ${vm.state.value.from} to ${vm.state.value.to}")
                putExtra(Intent.EXTRA_TEXT, csv)
            }
            runCatching { context.startActivity(Intent.createChooser(send, "Share sales report (CSV)")) }
        }
    }

    val today = remember { ReportRange.today() }
    val last7 = remember(today) { ReportRange.last7Days(today) }
    val month = remember(today) { ReportRange.thisMonth(today) }
    val pickedRange = state.from to state.to

    PullToRefreshBox(
        isRefreshing = state.isLoading,
        onRefresh = { vm.generate() },
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "header") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader("Sales report")
                    Text(
                        "Delivered-order totals use the delivery date. Cash collections below use the actual collection date, including orders not yet marked Delivered.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item(key = "range") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = pickedRange == today to today,
                            onClick = { vm.setRange(today, today) },
                            label = { Text("Today") }
                        )
                        FilterChip(
                            selected = pickedRange == last7,
                            onClick = { vm.setRange(last7.first, last7.second) },
                            label = { Text("Last 7 days") }
                        )
                        FilterChip(
                            selected = pickedRange == month,
                            onClick = { vm.setRange(month.first, month.second) },
                            label = { Text("This month") }
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { picker = PickerTarget.FROM }, modifier = Modifier.weight(1f)) {
                            Text("From ${state.from}")
                        }
                        OutlinedButton(onClick = { picker = PickerTarget.TO }, modifier = Modifier.weight(1f)) {
                            Text("To ${state.to}")
                        }
                    }
                }
            }

            state.error?.let { msg -> item(key = "error") { MessageCard(msg, isError = true) } }

            state.report?.let { report ->
                reportItems(report)
                item(key = "export") {
                    Button(
                        onClick = vm::exportCsv,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isExporting && !state.isLoading
                    ) {
                        if (state.isExporting) ButtonProgress() else Text("Share CSV")
                    }
                }
            }
        }
    }

    picker?.let { target ->
        val initial = if (target == PickerTarget.FROM) state.from else state.to
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { picker = null },
            confirmButton = {
                TextButton(onClick = {
                    val millis = pickerState.selectedDateMillis
                    if (millis != null) {
                        val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        if (target == PickerTarget.FROM) vm.setFrom(date) else vm.setTo(date)
                    }
                    picker = null
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { picker = null }) { Text("Cancel") } }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.reportItems(report: AdminSalesReport) {
    val summary = report.summary

    item(key = "metrics-1") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReportMetric(Modifier.weight(1f), "Delivered orders", summary.deliveredOrderCount.toString())
            ReportMetric(Modifier.weight(1f), "Delivered value", Money.format(summary.deliveredValueCents))
        }
    }
    item(key = "metrics-2") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReportMetric(Modifier.weight(1f), "Settled revenue", Money.format(summary.settledRevenueCents))
            ReportMetric(Modifier.weight(1f), "Outstanding", Money.format(summary.outstandingValueCents))
        }
    }
    item(key = "breakdown") {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "Payment breakdown: ${summary.settledOrderCount} settled · ${summary.outstandingOrderCount} outstanding",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (report.approximate) {
                Text(
                    "Totals are approximate (server report not installed and the range exceeds the download cap). Apply the admin reporting migration for exact totals.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    item(key = "cash-title") {
        Text("Cash collections · by collection date", style = MaterialTheme.typography.titleMedium)
    }
    item(key = "cash-metrics") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReportMetric(Modifier.weight(1f), "Cash collected", Money.format(report.cashCollectedCents))
            ReportMetric(Modifier.weight(1f), "Collections", report.cashCollectionCount.toString())
        }
    }

    if (report.cashCollections.isEmpty()) {
        item(key = "cash-empty") {
            Text(
                "No cash collections were recorded in this date range.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        report.cashCollections.forEachIndexed { index, collection ->
            item(key = "cash-$index-${collection.orderNo}") {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(13.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(collection.orderNo, fontWeight = FontWeight.SemiBold)
                            Text(
                                Formats.malaysiaDateTime(collection.collectedAt),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(Money.format(collection.amountCents), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        item(key = "cash-note") {
            Text(
                "Cash collections are shown by receipt date. Do not add this breakdown to the delivered-order totals above.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (report.recentOrders.isEmpty()) {
        item(key = "orders-empty") {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text("No delivered orders were found in this date range.", modifier = Modifier.padding(16.dp))
            }
        }
    } else {
        item(key = "orders-title") {
            Text("Delivered order details · latest 20", style = MaterialTheme.typography.titleMedium)
        }
        report.recentOrders.forEach { order ->
            item(key = "order-${order.id}") {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    Column(modifier = Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(order.orderNo, fontWeight = FontWeight.SemiBold)
                            Text(Money.format(order.totalCents))
                        }
                        Text(Formats.malaysiaDateTime(order.deliveredAt), style = MaterialTheme.typography.bodySmall)
                        Text(
                            "${PaymentMethod.label(order.paymentMethod)} · ${PaymentStatus.label(order.paymentStatus)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (order.paymentStatus in PaymentStatus.settled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportMetric(modifier: Modifier, title: String, value: String) {
    Card(modifier = modifier, shape = RoundedCornerShape(15.dp)) {
        Column(modifier = Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(title, style = MaterialTheme.typography.bodySmall)
        }
    }
}
