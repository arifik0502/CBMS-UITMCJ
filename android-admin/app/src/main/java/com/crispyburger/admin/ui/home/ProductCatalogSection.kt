package com.crispyburger.admin.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crispyburger.admin.data.ImageCompressor
import com.crispyburger.admin.data.Money
import com.crispyburger.admin.ui.common.ButtonProgress
import com.crispyburger.admin.ui.common.MessageCard
import com.crispyburger.admin.ui.common.RemoteImage
import com.crispyburger.admin.ui.common.SectionHeader
import com.crispyburger.admin.ui.common.StatusChip
import com.crispyburger.admin.ui.common.Tone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val PriceInput = Regex("^\\d{0,5}(\\.\\d{0,2})?$")
private val StockInput = Regex("^\\d{0,6}$")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(vm: CatalogViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching { ImageCompressor.toJpeg(context.contentResolver, uri) }.getOrNull()
            }
            if (bytes == null) vm.pictureProblem("That picture could not be used. Try a different one.")
            else vm.uploadPicture(bytes)
        }
    }

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
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    SectionHeader("Products")
                    Text(
                        "Manage everything customers see on the menu. Hiding a product keeps old orders and reports safe.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = vm::addNew,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isSaving
                    ) { Text("＋ Add new product") }
                }
            }

            state.success?.let { msg -> item(key = "success") { MessageCard(msg, isError = false) } }
            state.error?.let { msg -> item(key = "error") { MessageCard(msg, isError = true) } }

            if (state.products.isEmpty() && !state.isLoading) {
                item(key = "empty") {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "No products yet. Tap “Add new product” to create the first menu item.",
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            items(state.products, key = { it.id }) { product ->
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Column(modifier = Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        RemoteImage(
                            url = product.imageUrl,
                            contentDescription = product.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
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
                            StatusChip(
                                if (product.isActive) "Shown on website" else "Hidden",
                                if (product.isActive) Tone.Good else Tone.Neutral
                            )
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
            onDismissRequest = vm::dismissDraft,
            title = { Text(if (draft.isNew) "Add product" else "Edit " + draft.product?.name.orEmpty()) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 540.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    RemoteImage(
                        url = draft.imageUrl,
                        contentDescription = "Product photo preview",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { picker.launch("image/*") },
                            enabled = !state.isUploadingImage && !state.isSaving,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (state.isUploadingImage) ButtonProgress() else Text(if (draft.imageUrl == null) "Choose photo" else "Change photo")
                        }
                        if (draft.imageUrl != null) {
                            TextButton(
                                onClick = { vm.updateDraft { it.copy(imageUrl = null) } },
                                enabled = !state.isUploadingImage && !state.isSaving
                            ) { Text("Remove") }
                        }
                    }
                    Text(
                        "Use a clear square or landscape product photo. It is automatically compressed before upload.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = draft.sku,
                        onValueChange = { v -> vm.updateDraft { it.copy(sku = v.uppercase().take(20)) } },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("SKU / short code") },
                        supportingText = { Text("Example: CRISPY-BURGER") },
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

                    if (draft.isNew) {
                        OutlinedTextField(
                            value = draft.initialStock,
                            onValueChange = { v -> if (v.matches(StockInput)) vm.updateDraft { it.copy(initialStock = v) } },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Starting stock") },
                            supportingText = { Text("You can change stock later in the Stock tab.") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Show on website", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Turn this off instead of deleting a product. Past orders stay intact.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = draft.isActive,
                                onCheckedChange = { active -> vm.updateDraft { it.copy(isActive = active) } },
                                enabled = !state.isSaving
                            )
                        }
                    }

                    state.error?.let { MessageCard(it, isError = true) }
                }
            },
            confirmButton = {
                Button(
                    onClick = vm::save,
                    enabled = draft.isValid && !state.isSaving && !state.isUploadingImage
                ) {
                    if (state.isSaving) ButtonProgress()
                    else Text(if (draft.isNew) "Add product" else "Save changes")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = vm::dismissDraft,
                    enabled = !state.isSaving && !state.isUploadingImage
                ) { Text("Cancel") }
            }
        )
    }
}
