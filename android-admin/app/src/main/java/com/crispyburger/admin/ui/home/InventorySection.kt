package com.crispyburger.admin.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.sp
import com.crispyburger.admin.data.AdminInventoryItem
import com.crispyburger.admin.data.AdminSupabase
import kotlinx.coroutines.launch

@Composable
fun InventorySection(refreshKey: Int = 0) {
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf(emptyList<AdminInventoryItem>()) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    var selectedItem by remember { mutableStateOf<AdminInventoryItem?>(null) }
    var adjustmentType by remember { mutableStateOf("RESTOCK") }
    var quantityText by remember { mutableStateOf("") }
    var reasonText by remember { mutableStateOf("") }

    fun refresh() {
        scope.launch {
            isLoading = true
            errorMessage = null
            try {
                items = AdminSupabase.loadInventory()
            } catch (error: Exception) {
                errorMessage = AdminSupabase.friendlyError(error, "Could not load inventory.")
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(refreshKey) {
        refresh()
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Inventory management", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = { refresh() }, enabled = !isLoading && !isSaving) {
                Text("Refresh")
            }
        }

        Text(
            "Available stock = physical stock minus quantities reserved by open orders.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (successMessage != null) {
            Text(successMessage!!, color = MaterialTheme.colorScheme.primary)
        }
        if (errorMessage != null) {
            Text(errorMessage!!, color = MaterialTheme.colorScheme.error)
        }

        when {
            isLoading -> {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator()
                }
            }
            items.isEmpty() -> {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "No inventory rows found. Add products and inventory records in Supabase first.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            else -> {
                items.forEach { item ->
                    InventoryCard(
                        item = item,
                        enabled = !isSaving,
                        onAdjust = { type ->
                            selectedItem = item
                            adjustmentType = type
                            quantityText = ""
                            reasonText = ""
                            errorMessage = null
                            successMessage = null
                        }
                    )
                }
            }
        }
    }

    if (selectedItem != null) {
        val item = selectedItem!!
        val quantity = quantityText.toIntOrNull()
        val validQuantity = quantity != null &&
            quantity >= 0 &&
            (adjustmentType == "SET" || quantity > 0)
        val resultingPhysicalStock = when (adjustmentType) {
            "RESTOCK" -> quantity?.let { item.stock.physicalStock + it }
            "REDUCTION" -> quantity?.let { item.stock.physicalStock - it }
            "SET" -> quantity
            else -> null
        }
        val meetsReservation = resultingPhysicalStock != null &&
            resultingPhysicalStock >= item.stock.reservedStock
        val reservationMessage = if (resultingPhysicalStock != null && !meetsReservation) {
            "This adjustment would leave ${resultingPhysicalStock} physical units, but ${item.stock.reservedStock} are reserved. Keep physical stock at or above ${item.stock.reservedStock}."
        } else null
        AlertDialog(
            onDismissRequest = { if (!isSaving) selectedItem = null },
            title = {
                Text(
                    when (adjustmentType) {
                        "RESTOCK" -> "Restock ${item.product.name}"
                        "REDUCTION" -> "Reduce ${item.product.name}"
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
                        value = quantityText,
                        onValueChange = { value ->
                            quantityText = value.filter { it.isDigit() }.take(6)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(if (adjustmentType == "SET") "New physical stock" else "Quantity") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = reasonText,
                        onValueChange = { reasonText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Reason (required)") },
                        minLines = 2
                    )
                    Text(
                        when (adjustmentType) {
                            "RESTOCK" -> "The entered quantity will be added to physical stock."
                            "REDUCTION" -> "The entered quantity will be removed. Physical stock must remain at or above reserved stock."
                            else -> "The entered value becomes physical stock. It cannot be lower than reserved stock."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (reservationMessage != null) {
                        Text(
                            reservationMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val productId = item.product.id
                        val amount = quantity ?: 0
                        val reason = reasonText.trim()
                        scope.launch {
                            isSaving = true
                            errorMessage = null
                            successMessage = null
                            try {
                                AdminSupabase.adjustInventory(productId, adjustmentType, amount, reason)
                                items = AdminSupabase.loadInventory()
                                selectedItem = null
                                successMessage = "Inventory updated and logged."
                            } catch (error: Exception) {
                                errorMessage = AdminSupabase.friendlyError(error, "Inventory adjustment failed. Check your connection and inventory permissions.")
                            } finally {
                                isSaving = false
                            }
                        }
                    },
                    enabled = validQuantity && meetsReservation && reasonText.trim().length >= 3 && !isSaving
                ) {
                    if (isSaving) CircularProgressIndicator()
                    else Text("Save adjustment")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedItem = null }, enabled = !isSaving) {
                    Text("Back")
                }
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(item.product.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "${item.product.sku} · ${formatInventoryPrice(item.product.priceCents)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                StockValue("Physical", item.stock.physicalStock)
                StockValue("Reserved", item.stock.reservedStock)
                StockValue("Available", item.stock.availableStock)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                OutlinedButton(
                    onClick = { onAdjust("RESTOCK") },
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 2.dp, vertical = 8.dp)
                ) {
                    Text("Restock", maxLines = 1, softWrap = false, fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = { onAdjust("REDUCTION") },
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 2.dp, vertical = 8.dp)
                ) {
                    Text("Reduce", maxLines = 1, softWrap = false, fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = { onAdjust("SET") },
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 2.dp, vertical = 8.dp)
                ) {
                    Text("Set", maxLines = 1, softWrap = false, fontSize = 12.sp)
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

private fun formatInventoryPrice(cents: Int): String {
    return "RM ${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"
}
