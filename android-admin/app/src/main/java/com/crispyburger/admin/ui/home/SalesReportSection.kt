package com.crispyburger.admin.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.crispyburger.admin.data.AdminCashCollection
import com.crispyburger.admin.data.AdminSalesOrder
import com.crispyburger.admin.data.AdminSupabase
import com.crispyburger.admin.data.SalesReportCalculator
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun SalesReportSection(refreshKey: Int = 0) {
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now(ZoneId.of("Asia/Kuala_Lumpur")) }
    var fromText by remember { mutableStateOf(today.minusDays(6).toString()) }
    var toText by remember { mutableStateOf(today.toString()) }
    var orders by remember { mutableStateOf(emptyList<AdminSalesOrder>()) }
    var cashCollections by remember { mutableStateOf(emptyList<AdminCashCollection>()) }
    var isLoading by remember { mutableStateOf(false) }
    var hasLoaded by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun loadReport() {
        val from = runCatching { LocalDate.parse(fromText.trim()) }.getOrNull()
        val to = runCatching { LocalDate.parse(toText.trim()) }.getOrNull()

        if (from == null || to == null) {
            errorMessage = "Use date format YYYY-MM-DD."
            return
        }
        if (to.isBefore(from)) {
            errorMessage = "End date must be on or after start date."
            return
        }
        if (to.toEpochDay() - from.toEpochDay() > 366) {
            errorMessage = "Choose a range of no more than 367 calendar days."
            return
        }

        scope.launch {
            isLoading = true
            errorMessage = null
            try {
                val deliveredOrders = AdminSupabase.loadDeliveredSales(from, to)
                val collectedCash = AdminSupabase.loadCashCollections(from, to)
                orders = deliveredOrders
                cashCollections = collectedCash
                hasLoaded = true
            } catch (error: Exception) {
                errorMessage = AdminSupabase.friendlyError(error, "Could not load sales report.")
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(refreshKey) {
        loadReport()
    }

    val summary = remember(orders) {
        SalesReportCalculator.calculate(orders)
    }
    val cashCollectedCents = remember(cashCollections) {
        cashCollections.sumOf { it.amountCents.toLong() }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Sales report", style = MaterialTheme.typography.titleLarge)
        Text(
            "Delivered-order totals use the delivery date. Cash collections below use the actual collection date, including orders not yet marked Delivered.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = fromText,
                onValueChange = { fromText = it },
                modifier = Modifier.weight(1f),
                label = { Text("From (YYYY-MM-DD)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                singleLine = true
            )
            OutlinedTextField(
                value = toText,
                onValueChange = { toText = it },
                modifier = Modifier.weight(1f),
                label = { Text("To (YYYY-MM-DD)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                singleLine = true
            )
        }

        Button(
            onClick = { loadReport() },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            Text("Generate report")
        }

        if (errorMessage != null) {
            Text(errorMessage!!, color = MaterialTheme.colorScheme.error)
        }

        if (isLoading) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
            }
        } else if (hasLoaded) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReportMetric(
                    modifier = Modifier.weight(1f),
                    title = "Delivered orders",
                    value = summary.deliveredOrderCount.toString()
                )
                ReportMetric(
                    modifier = Modifier.weight(1f),
                    title = "Delivered value",
                    value = formatReportRinggit(summary.deliveredValueCents)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReportMetric(
                    modifier = Modifier.weight(1f),
                    title = "Settled revenue",
                    value = formatReportRinggit(summary.settledRevenueCents)
                )
                ReportMetric(
                    modifier = Modifier.weight(1f),
                    title = "Outstanding",
                    value = formatReportRinggit(summary.outstandingValueCents)
                )
            }

            Text(
                "Payment breakdown: ${summary.settledOrderCount} settled · ${summary.outstandingOrderCount} outstanding",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )

            Text("Cash collections · by collection date", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReportMetric(
                    modifier = Modifier.weight(1f),
                    title = "Cash collected",
                    value = formatReportRinggit(cashCollectedCents)
                )
                ReportMetric(
                    modifier = Modifier.weight(1f),
                    title = "Collections",
                    value = cashCollections.size.toString()
                )
            }

            if (cashCollections.isEmpty()) {
                Text(
                    "No cash collections were recorded in this date range.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                cashCollections.take(20).forEach { collection ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(13.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(collection.orderNo, fontWeight = FontWeight.SemiBold)
                                Text(
                                    formatMalaysiaDateTime(collection.collectedAt),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                formatReportRinggit(collection.amountCents.toLong()),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                Text(
                    "Cash collections are shown by receipt date. Do not add this breakdown to the delivered-order totals above.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (orders.isEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "No delivered orders were found in this date range.",
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                Text("Delivered order details · latest 20", style = MaterialTheme.typography.titleMedium)
                orders.take(20).forEach { order ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(13.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(order.orderNo, fontWeight = FontWeight.SemiBold)
                                Text(formatReportRinggit(order.totalCents.toLong()))
                            }
                            Text(formatMalaysiaDateTime(order.deliveredAt), style = MaterialTheme.typography.bodySmall)
                            Text(
                                "${order.paymentMethod} · ${reportStatus(order.paymentStatus)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (order.paymentStatus == "VERIFIED" || order.paymentStatus == "CASH_COLLECTED") {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.error
                                }
                            )
                        }
                    }
                }
                Text(
                    "This report retrieves up to 500 delivered orders per date range. The order list shows the latest 20.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ReportMetric(
    modifier: Modifier,
    title: String,
    value: String
) {
    Card(modifier = modifier, shape = RoundedCornerShape(15.dp)) {
        Column(
            modifier = Modifier.padding(13.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(title, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun formatMalaysiaDateTime(value: String): String {
    return runCatching {
        Instant.parse(value)
            .atZone(ZoneId.of("Asia/Kuala_Lumpur"))
            .format(DateTimeFormatter.ofPattern("dd MMM yyyy, h:mm a", Locale.ENGLISH))
    }.getOrDefault(value.take(10))
}

private fun formatReportRinggit(cents: Long): String {
    return "RM ${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"
}

private fun reportStatus(value: String): String {
    return value.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
}
