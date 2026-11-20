package com.crispyburger.admin.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crispyburger.admin.data.Money
import com.crispyburger.admin.ui.common.ButtonProgress
import com.crispyburger.admin.ui.common.MessageCard
import com.crispyburger.admin.ui.common.SectionHeader
import com.crispyburger.admin.ui.common.StatusChip
import com.crispyburger.admin.ui.common.Tone

private val PriceInput = Regex("^\\d{0,5}(\\.\\d{0,2})?$")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(vm: CatalogViewModel) {
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
                    SectionHeader("Product catalogue")
                    Text(
                        "Edit product details and pricing. Price changes affect new orders only; existing orders keep their original price.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            state.success?.let { msg -> item(key = "success") { MessageCard(msg, isError = false) } }
            state.error?.let { msg -> item(key = "error") { MessageCard(msg, isError = true) } }

            if (state.products.isEmpty() && !state.isLoading) {
                item(key = "empty") {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "No products found. Add the initial product in Supabase before editing the catalogue.",
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            items(state.products, key = { it.id }) { product ->
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Column(modifier = Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                product.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            Text(Money.format(product.priceCents), fontWeight = FontWeight.Bold)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                product.sku,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            StatusChip(if (product.isActive) "Active" else "Inactive", if (product.isActive) Tone.Good else Tone.Neutral)
                        }
                        if (product.description.isNotBlank()) {
                            Text(product.description, style = MaterialTheme.typography.bodySmall)
                        }
                        OutlinedButton(
                            onClick = { vm.edit(product) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.isSaving
                        ) { Text("Edit product") }
                    }
                }
            }
        }
    }

    state.draft?.let { draft ->
        AlertDialog(
            onDismissRequest = { if (!state.isSaving) vm.dismissDraft() },
            title = { Text("Edit ${draft.product.name}") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    OutlinedTextField(
                        value = draft.sku,
                        onValueChange = { v -> vm.updateDraft { it.copy(sku = v.uppercase().take(20)) } },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("SKU") },
                        supportingText = { Text("2-20 letters, numbers, hyphens or underscores") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = draft.name,
                        onValueChange = { v -> vm.updateDraft { it.copy(name = v.take(80)) } },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Product name") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = draft.description,
                        onValueChange = { v -> vm.updateDraft { it.copy(description = v.take(500)) } },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Description") },
                        minLines = 2,
                        maxLines = 4
                    )
                    OutlinedTextField(
                        value = draft.price,
                        onValueChange = { v -> if (v.matches(PriceInput)) vm.updateDraft { it.copy(price = v) } },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Price (RM)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                    Text(
                        "This updates the catalogue price for future orders. Existing order totals are not changed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(onClick = vm::save, enabled = draft.isValid && !state.isSaving) {
                    if (state.isSaving) ButtonProgress() else Text("Save changes")
                }
            },
            dismissButton = {
                TextButton(onClick = vm::dismissDraft, enabled = !state.isSaving) { Text("Cancel") }
            }
        )
    }
}
