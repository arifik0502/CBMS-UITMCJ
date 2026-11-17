package com.crispyburger.admin.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.crispyburger.admin.data.AdminProduct
import com.crispyburger.admin.data.AdminSupabase
import com.crispyburger.admin.data.ProductValidation
import kotlinx.coroutines.launch

@Composable
fun ProductCatalogSection(refreshKey: Int = 0) {
    val scope = rememberCoroutineScope()
    var products by remember { mutableStateOf(emptyList<AdminProduct>()) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    var selectedProduct by remember { mutableStateOf<AdminProduct?>(null) }
    var skuText by remember { mutableStateOf("") }
    var nameText by remember { mutableStateOf("") }
    var descriptionText by remember { mutableStateOf("") }
    var priceText by remember { mutableStateOf("") }

    fun refresh() {
        scope.launch {
            isLoading = true
            errorMessage = null
            try {
                products = AdminSupabase.loadProducts()
            } catch (error: Exception) {
                errorMessage = AdminSupabase.friendlyError(error, "Could not load product catalogue.")
            } finally {
                isLoading = false
            }
        }
    }

    fun edit(product: AdminProduct) {
        selectedProduct = product
        skuText = product.sku
        nameText = product.name
        descriptionText = product.description
        priceText = ProductValidation.formatPrice(product.priceCents)
        errorMessage = null
        successMessage = null
    }

    LaunchedEffect(refreshKey) {
        refresh()
    }

    val parsedPrice = ProductValidation.priceCents(priceText)
    val canSave = ProductValidation.normalizeSku(skuText) != null &&
        nameText.trim().length in 2..80 &&
        descriptionText.trim().length <= 500 &&
        parsedPrice != null &&
        !isSaving

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Product catalogue", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = { refresh() }, enabled = !isLoading && !isSaving) {
                Text("Refresh")
            }
        }

        Text(
            "Edit product details and pricing. Price changes affect new orders only; existing orders keep their original price.",
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            products.isEmpty() -> {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "No products found. Add the initial product in Supabase before editing the catalogue.",
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            else -> {
                products.forEach { product ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(15.dp),
                            verticalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    product.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    formatCatalogPrice(product.priceCents),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                "${product.sku} · ${if (product.isActive) "Active" else "Inactive"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (product.description.isNotBlank()) {
                                Text(product.description, style = MaterialTheme.typography.bodySmall)
                            }
                            OutlinedButton(
                                onClick = { edit(product) },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isSaving
                            ) {
                                Text("Edit product")
                            }
                        }
                    }
                }
            }
        }
    }

    if (selectedProduct != null) {
        val product = selectedProduct!!
        AlertDialog(
            onDismissRequest = { if (!isSaving) selectedProduct = null },
            title = { Text("Edit ${product.name}") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    OutlinedTextField(
                        value = skuText,
                        onValueChange = { skuText = it.uppercase().take(20) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("SKU") },
                        supportingText = { Text("2-20 letters, numbers, hyphens or underscores") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = nameText,
                        onValueChange = { nameText = it.take(80) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Product name") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = descriptionText,
                        onValueChange = { descriptionText = it.take(500) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Description") },
                        minLines = 2,
                        maxLines = 4
                    )
                    OutlinedTextField(
                        value = priceText,
                        onValueChange = { value ->
                            if (value.matches(Regex("^\\d{0,5}(\\.\\d{0,2})?$"))) {
                                priceText = value
                            }
                        },
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
                Button(
                    onClick = {
                        val cents = parsedPrice ?: return@Button
                        scope.launch {
                            isSaving = true
                            errorMessage = null
                            successMessage = null
                            try {
                                AdminSupabase.updateProductDetails(
                                    productId = product.id,
                                    sku = skuText,
                                    name = nameText,
                                    description = descriptionText,
                                    priceCents = cents
                                )
                                products = AdminSupabase.loadProducts()
                                selectedProduct = null
                                successMessage = "Product details updated."
                            } catch (error: Exception) {
                                errorMessage = AdminSupabase.friendlyError(
                                    error,
                                    "Could not update the product. Check the SKU and developer permissions."
                                )
                            } finally {
                                isSaving = false
                            }
                        }
                    },
                    enabled = canSave
                ) {
                    if (isSaving) CircularProgressIndicator()
                    else Text("Save changes")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { selectedProduct = null },
                    enabled = !isSaving
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun formatCatalogPrice(cents: Int): String {
    return "RM ${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"
}
