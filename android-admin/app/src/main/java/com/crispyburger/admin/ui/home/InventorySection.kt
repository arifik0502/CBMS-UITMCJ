package com.crispyburger.admin.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crispyburger.admin.data.AdminInventoryItem
import com.crispyburger.admin.data.InventoryRules
import com.crispyburger.admin.data.Money
import com.crispyburger.admin.ui.common.ButtonProgress
import com.crispyburger.admin.ui.common.MessageCard
import com.crispyburger.admin.ui.common.SectionHeader
import com.crispyburger.admin.ui.common.StatusChip
import com.crispyburger.admin.ui.common.Tone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(vm: InventoryViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.ensureLoaded() }

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
                    SectionHeader("Inventory management")
                    Text(
                        "Available stock = physical stock minus quantities reserved by open orders. " +
                            "Items at ${InventoryRules.LOW_STOCK_THRESHOLD} or fewer available are marked Low.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            state.success?.let { msg -> item(key = "success") { MessageCard(msg, isError = false) } }
            state.error?.let { msg -> item(key = "error") { MessageCard(msg, isError = true) } }

            if (state.items.isEmpty() && !state.isLoading) {
                item(key = "empty") {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "No inventory rows found. Add products and inventory records in Supabase first.",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            items(state.items, key = { it.product.id }) { item ->
                InventoryCard(
                    item = item,
                    enabled = !state.isSaving,
                    onAdjust = { type -> vm.startAdjust(item, type) }
                )
            }
        }
    }

    state.draft?.let { draft ->
        val item = draft.item
        val resulting = draft.resultingPhysical
        AlertDialog(
            onDismissRequest = { if (!state.isSaving) vm.dismissDraft() },
            title = {
                Text(
                    when (draft.type) {
                        InventoryRules.RESTOCK -> "Restock ${item.product.name}"
                        InventoryRules.REDUCTION -> "Reduce ${item.product.name}"
                        else -> "Set ${item.product.name} stock"
                    }
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Current physical stock: ${item.stock.physicalStock}")
                    Text("Reserved stock: ${item.stock.reservedStock}")
                    Text("Available stock: ${item.stock.availableStock}")
                    OutlinedTextField(
                        value = draft.quantityText,
                        onValueChange = vm::updateQuantity,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(if (draft.type == InventoryRules.SET) "New physical stock" else "Quantity") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = draft.reasonText,
                        onValueChange = vm::updateReason,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Reason (required)") },
                        minLines = 2
                    )
                    Text(
                        when (draft.type) {
                            InventoryRules.RESTOCK -> "The entered quantity will be added to physical stock."
                            InventoryRules.REDUCTION -> "The entered quantity will be removed. Physical stock must remain at or above reserved stock."
                            else -> "The entered value becomes physical stock. It cannot be lower than reserved stock."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (resulting != null && !draft.meetsReservation) {
                        Text(
                            "This adjustment would leave $resulting physical units, but ${item.stock.reservedStock} are reserved. Keep physical stock at or above ${item.stock.reservedStock}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = vm::save, enabled = draft.canSave && !state.isSaving) {
                    if (state.isSaving) ButtonProgress() else Text("Save adjustment")
                }
            },
            dismissButton = {
                TextButton(onClick = vm::dismissDraft, enabled = !state.isSaving) { Text("Back") }
            }
        )
    }
}

@Composable
private fun InventoryCard(
    item: AdminInventoryItem,
    enabled: Boolean,
    onAdjust: (String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    item.product.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                if (InventoryRules.isLow(item.stock.availableStock)) {
                    StatusChip(if (item.stock.availableStock <= 0) "Out of stock" else "Low", Tone.Bad)
                }
            }
            Text(
                "${item.product.sku} · ${Money.format(item.product.priceCents)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                StockValue("Physical", item.stock.physicalStock)
                StockValue("Reserved", item.stock.reservedStock)
                StockValue("Available", item.stock.availableStock)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(
                    "Restock" to InventoryRules.RESTOCK,
                    "Reduce" to InventoryRules.REDUCTION,
                    "Set" to InventoryRules.SET
                ).forEach { (label, type) ->
                    OutlinedButton(
                        onClick = { onAdjust(type) },
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp)
                    ) {
                        Text(label, maxLines = 1, softWrap = false, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun StockValue(label: String, value: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
    }
}
