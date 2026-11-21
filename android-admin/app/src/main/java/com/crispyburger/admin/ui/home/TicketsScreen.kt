package com.crispyburger.admin.ui.home

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crispyburger.admin.data.AdminTicket
import com.crispyburger.admin.data.Formats
import com.crispyburger.admin.data.TicketFilter
import com.crispyburger.admin.data.TicketRules
import com.crispyburger.admin.data.TicketStatus
import com.crispyburger.admin.ui.common.ButtonProgress
import com.crispyburger.admin.ui.common.ConfirmDialog
import com.crispyburger.admin.ui.common.MessageCard
import com.crispyburger.admin.ui.common.SectionHeader
import com.crispyburger.admin.ui.common.StatusChip
import com.crispyburger.admin.ui.common.Tone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketsScreen(vm: TicketsViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(Unit) { vm.ensureLoaded() }

    val visible = state.visible

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
            item(key = "header") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader("Support tickets")
                    Text(
                        "Problems and questions sent from the customer website. Call the customer if needed, then mark the ticket resolved.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TicketFilter.entries.forEach { filter ->
                            val count = TicketRules.filter(state.tickets, filter).size
                            FilterChip(
                                selected = state.filter == filter,
                                onClick = { vm.setFilter(filter) },
                                label = { Text("${filter.label} ($count)") }
                            )
                        }
                    }
                }
            }

            state.info?.let { msg -> item(key = "info") { MessageCard(msg, isError = false) } }
            state.error?.let { msg -> item(key = "error") { MessageCard(msg, isError = true) } }

            if (visible.isEmpty() && !state.isLoading) {
                item(key = "empty") {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            if (state.filter == TicketFilter.OPEN) "No open tickets. Nice work." else "No resolved tickets yet.",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            items(visible, key = { it.id }) { ticket ->
                TicketCard(ticket) { vm.select(ticket.id) }
            }
        }
    }

    state.selected?.let { ticket ->
        TicketDetailDialog(
            ticket = ticket,
            working = state.isWorking,
            error = state.error,
            photoLoading = state.photo is ReceiptState.Loading,
            onClose = vm::dismissDetail,
            onToggle = vm::toggleStatus,
            onViewPhoto = vm::viewPhoto,
            onCall = { number ->
                runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))) }
            }
        )
    }

    if (state.photo is ReceiptState.Ready || state.photo is ReceiptState.Failed) {
        ReceiptViewerDialog(
            receipt = state.photo,
            onClose = vm::dismissPhoto,
            onOpenBrowser = { url ->
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            }
        )
    }
}

@Composable
private fun TicketCard(ticket: AdminTicket, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(ticket.ticketNo, fontWeight = FontWeight.SemiBold)
                StatusChip(
                    TicketStatus.label(ticket.status),
                    if (ticket.status == TicketStatus.RESOLVED) Tone.Good else Tone.Warn
                )
            }
            Text(TicketRules.preview(ticket.message), style = MaterialTheme.typography.bodyLarge, maxLines = 2)
            Text(
                buildString {
                    append(ticket.customerName)
                    ticket.orderNo?.let { append(" · Order ").append(it) } ?: append(" · General question")
                    if (ticket.attachmentPath != null) append(" · Photo")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                Formats.malaysiaDateTime(ticket.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
private fun TicketDetailDialog(
    ticket: AdminTicket,
    working: Boolean,
    error: String?,
    photoLoading: Boolean,
    onClose: () -> Unit,
    onToggle: () -> Unit,
    onViewPhoto: () -> Unit,
    onCall: (String) -> Unit
) {
    var confirm by remember { mutableStateOf(false) }
    val resolving = ticket.status == TicketStatus.OPEN
    val dial = TicketRules.dialable(ticket.customerPhone)

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
                    Text(ticket.ticketNo, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
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
                    error?.let { MessageCard(it, isError = true) }

                    StatusChip(
                        TicketStatus.label(ticket.status),
                        if (resolving) Tone.Warn else Tone.Good
                    )

                    Line("Customer", ticket.customerName)
                    Line("Phone", ticket.customerPhone)
                    Line("About", ticket.orderNo?.let { "Order $it" } ?: "General question")
                    Line("Sent", Formats.malaysiaDateTime(ticket.createdAt))
                    if (!ticket.resolvedAt.isNullOrBlank()) Line("Resolved", Formats.malaysiaDateTime(ticket.resolvedAt))

                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Message", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                            Text(ticket.message, style = MaterialTheme.typography.bodyLarge)
                        }
                    }

                    if (dial != null) {
                        OutlinedButton(onClick = { onCall(dial) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Call ${ticket.customerName}")
                        }
                    }

                    if (ticket.attachmentPath != null) {
                        OutlinedButton(
                            onClick = onViewPhoto,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !photoLoading
                        ) {
                            Text(if (photoLoading) "Opening photo…" else "View photo")
                        }
                    }

                    Button(
                        onClick = { confirm = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !working
                    ) {
                        if (working) ButtonProgress() else Text(if (resolving) "Mark as resolved" else "Reopen ticket")
                    }
                }
            }
        }
    }

    if (confirm) {
        ConfirmDialog(
            title = if (resolving) "Mark as resolved" else "Reopen ticket",
            message = if (resolving) {
                "Mark ${ticket.ticketNo} as resolved? The customer will see it as resolved under My orders."
            } else {
                "Reopen ${ticket.ticketNo}? The customer will see it as open again."
            },
            confirmLabel = if (resolving) "Resolve" else "Reopen",
            onConfirm = { confirm = false; onToggle() },
            onDismiss = { confirm = false }
        )
    }
}

@Composable
private fun Line(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
        Text(value, style = MaterialTheme.typography.bodyMedium)
        HorizontalDivider()
    }
}
