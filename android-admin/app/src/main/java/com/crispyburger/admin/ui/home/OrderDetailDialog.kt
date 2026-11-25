package com.crispyburger.admin.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.crispyburger.admin.data.AdminOrder
import com.crispyburger.admin.data.Formats
import com.crispyburger.admin.data.Money
import com.crispyburger.admin.data.OrderRules
import com.crispyburger.admin.data.OrderStatus
import com.crispyburger.admin.data.PaymentMethod
import com.crispyburger.admin.data.PaymentStatus
import com.crispyburger.admin.ui.common.ButtonProgress
import com.crispyburger.admin.ui.common.ConfirmDialog
import com.crispyburger.admin.ui.common.MessageCard
import com.crispyburger.admin.ui.common.StatusChip
import com.crispyburger.admin.ui.common.orderTone
import com.crispyburger.admin.ui.common.paymentTone

private data class ConfirmSpec(
    val title: String,
    val message: String,
    val confirmLabel: String,
    val action: OrderAction
)

private enum class ReasonKind { REJECT, CANCEL }

@Composable
fun OrderDetailDialog(
    order: AdminOrder,
    state: OrdersUiState,
    onClose: () -> Unit,
    onAction: (OrderAction) -> Unit,
    onViewReceipt: () -> Unit
) {
    var confirm by remember { mutableStateOf<ConfirmSpec?>(null) }
    var reasonKind by remember { mutableStateOf<ReasonKind?>(null) }
    var reasonText by remember { mutableStateOf("") }
    val working = state.isWorking

    Dialog(
        onDismissRequest = { if (!working) onClose() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(order.orderNo, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    TextButton(onClick = onClose, enabled = !working) { Text("Close") }
                }
                HorizontalDivider()

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    state.error?.let { MessageCard(it, isError = true) }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusChip(OrderStatus.label(order.orderStatus), orderTone(order.orderStatus))
                        StatusChip(PaymentStatus.label(order.paymentStatus), paymentTone(order.paymentStatus))
                    }

                    DetailLine("Customer", order.customerName)
                    DetailLine("Phone", order.customerPhone)
                    DetailLine("Location", order.deliveryAddress)
                    DetailLine("Payment", PaymentMethod.label(order.paymentMethod))
                    DetailLine("Total", Money.format(order.totalCents))
                    DetailLine("Ordered", Formats.malaysiaDateTime(order.createdAt))
                    DetailLine("Delivery trip", order.tripLabel)
                    if (order.tripWindow.isNotBlank()) DetailLine("Trip window", order.tripWindow)
                    if (!order.tripDate.isNullOrBlank()) DetailLine("Trip date", order.tripDate)
                    if (!order.note.isNullOrBlank()) DetailLine("Customer note", order.note)

                    ItemsSection(state.items)

                    val rejection = state.payment?.rejectionReason
                    if (!rejection.isNullOrBlank()) DetailLine("Rejection reason", rejection)

                    if (state.paymentLoading) {
                        CircularProgressIndicator()
                    } else if (state.payment?.receiptPath != null) {
                        OutlinedButton(
                            onClick = onViewReceipt,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !working && state.receipt is ReceiptState.None
                        ) { Text("View payment receipt") }
                    } else if (OrderRules.awaitingVerification(order)) {
                        Text(
                            "No receipt path was returned. Check the payment row and private receipts bucket permissions.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    if (OrderRules.awaitingVerification(order)) {
                        Button(
                            onClick = {
                                confirm = ConfirmSpec(
                                    "Verify payment",
                                    "Confirm the DuitNow receipt for ${order.orderNo} (${Money.format(order.totalCents)}) is genuine and the amount matches.",
                                    "Verify payment",
                                    OrderAction.Verify
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !working && state.payment?.receiptPath != null
                        ) { if (working) ButtonProgress() else Text("Verify payment") }
                        OutlinedButton(
                            onClick = { reasonText = ""; reasonKind = ReasonKind.REJECT },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !working
                        ) { Text("Reject payment") }
                    }

                    if (OrderRules.canCollectCash(order)) {
                        Button(
                            onClick = {
                                confirm = ConfirmSpec(
                                    "Confirm cash collected",
                                    "Confirm ${Money.format(order.totalCents)} cash was received for ${order.orderNo}. This cannot be undone here.",
                                    "Cash collected",
                                    OrderAction.CollectCash
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !working
                        ) { Text("Confirm cash collected") }
                    }

                    val next = OrderRules.nextStatus(order)
                    if (next != null) {
                        val label = when (next) {
                            OrderStatus.OUT_FOR_DELIVERY -> "Mark out for delivery"
                            OrderStatus.DELIVERED -> "Mark as delivered"
                            else -> "Move to ${OrderStatus.label(next)}"
                        }
                        OutlinedButton(
                            onClick = {
                                confirm = ConfirmSpec(
                                    label,
                                    "Move ${order.orderNo} from ${OrderStatus.label(order.orderStatus)} to ${OrderStatus.label(next)}?",
                                    label,
                                    OrderAction.Advance(next)
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !working && OrderRules.canAdvance(order)
                        ) { Text(label) }
                    }

                    if (OrderRules.deliveryBlockedByPayment(order)) {
                        Text(
                            "Verify the DuitNow payment before marking this order as delivered.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    if (OrderRules.canCancel(order)) {
                        OutlinedButton(
                            onClick = { reasonText = ""; reasonKind = ReasonKind.CANCEL },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !working
                        ) {
                            Text(if (order.paymentStatus == PaymentStatus.CASH_PENDING) "Cancel cash order" else "Cancel order")
                        }
                    }
                }
            }
        }
    }

    confirm?.let { spec ->
        ConfirmDialog(
            title = spec.title,
            message = spec.message,
            confirmLabel = spec.confirmLabel,
            onConfirm = {
                confirm = null
                onAction(spec.action)
            },
            onDismiss = { confirm = null }
        )
    }

    reasonKind?.let { kind ->
        val isReject = kind == ReasonKind.REJECT
        AlertDialog(
            onDismissRequest = { reasonKind = null },
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
                        val reason = reasonText.trim()
                        if (reason.length >= 3) {
                            reasonKind = null
                            onAction(if (isReject) OrderAction.Reject(reason) else OrderAction.Cancel(reason))
                        }
                    },
                    enabled = reasonText.trim().length >= 3 && !working
                ) { Text(if (isReject) "Reject payment" else "Cancel order") }
            },
            dismissButton = { TextButton(onClick = { reasonKind = null }) { Text("Back") } }
        )
    }
}

@Composable
private fun ItemsSection(items: ItemsState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Items", style = MaterialTheme.typography.titleMedium)
            when (items) {
                ItemsState.None, ItemsState.Loading -> CircularProgressIndicator()
                ItemsState.Unavailable -> Text(
                    "Item details are unavailable. Check that order_items is readable by the OWNER account.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                is ItemsState.Loaded -> if (items.items.isEmpty()) {
                    Text(
                        "No item lines were returned for this order.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    items.items.forEach { line ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("${line.quantity} × ${line.productName}", modifier = Modifier.weight(1f))
                            Text(Money.format(line.displayTotalCents), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
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

@Composable
fun ReceiptViewerDialog(
    receipt: ReceiptState,
    onClose: () -> Unit,
    onOpenBrowser: (String) -> Unit
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Payment receipt", style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = onClose) { Text("Close") }
                }
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    when (receipt) {
                        ReceiptState.None, ReceiptState.Loading -> CircularProgressIndicator()
                        is ReceiptState.Failed -> Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Text("This receipt could not be shown in the app (it may be a PDF).")
                            Button(onClick = { onOpenBrowser(receipt.url) }) { Text("Open in browser") }
                        }
                        is ReceiptState.Ready -> {
                            var scale by remember { mutableFloatStateOf(1f) }
                            var offset by remember { mutableStateOf(Offset.Zero) }
                            Image(
                                bitmap = receipt.bitmap.asImageBitmap(),
                                contentDescription = "Payment receipt",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(Unit) {
                                        detectTransformGestures { _, pan, zoom, _ ->
                                            scale = (scale * zoom).coerceIn(1f, 5f)
                                            offset = if (scale == 1f) Offset.Zero else offset + pan
                                        }
                                    }
                                    .graphicsLayer(
                                        scaleX = scale,
                                        scaleY = scale,
                                        translationX = offset.x,
                                        translationY = offset.y
                                    )
                            )
                        }
                    }
                }
                Text(
                    "Pinch to zoom. The link expires after 5 minutes.",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
