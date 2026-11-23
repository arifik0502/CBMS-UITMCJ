package com.crispyburger.admin.ui.home

import android.app.TimePickerDialog
import android.content.Context
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crispyburger.admin.ui.common.ButtonProgress
import com.crispyburger.admin.ui.common.MessageCard
import com.crispyburger.admin.ui.common.SectionHeader
import com.crispyburger.admin.ui.common.StatusChip
import com.crispyburger.admin.ui.common.Tone
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val IntInput = Regex("^\\d{0,3}$")
private val FloorInput = Regex("^[0-9, ]{0,160}$")

private fun displayTime(value: String): String =
    runCatching {
        LocalTime.parse(value.take(8), DateTimeFormatter.ofPattern("HH:mm:ss"))
            .format(DateTimeFormatter.ofPattern("h:mm a", Locale.US))
    }.getOrDefault(value.take(5))

private fun pickTime(context: Context, current: String, onPicked: (String) -> Unit) {
    val initial = runCatching {
        LocalTime.parse(current.take(8), DateTimeFormatter.ofPattern("HH:mm:ss"))
    }.getOrDefault(LocalTime.of(19, 0))
    TimePickerDialog(
        context,
        { _, hour, minute -> onPicked(String.format(Locale.US, "%02d:%02d:00", hour, minute)) },
        initial.hour,
        initial.minute,
        false
    ).show()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeliverySettingsScreen(vm: DeliverySettingsViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val settings = state.settings

    var tripDraft by remember { mutableStateOf<TripDraft?>(null) }
    var buildingDraft by remember { mutableStateOf<BuildingDraft?>(null) }
    var acceptingOrders by rememberSaveable { mutableStateOf(true) }
    var maxQtyText by rememberSaveable { mutableStateOf("10") }

    LaunchedEffect(Unit) { vm.ensureLoaded() }
    LaunchedEffect(settings?.acceptingOrders, settings?.maxQtyPerOrder) {
        settings?.let {
            acceptingOrders = it.acceptingOrders
            maxQtyText = it.maxQtyPerOrder.toString()
        }
    }

    PullToRefreshBox(
        isRefreshing = state.isLoading,
        onRefresh = vm::refresh,
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item("header") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionHeader("Delivery & ordering")
                    Text(
                        "Simple controls for the live customer website. Payment and security settings stay locked.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            state.success?.let { item("success") { MessageCard(it, isError = false) } }
            state.error?.let { item("error") { MessageCard(it, isError = true) } }

            item("order-controls") {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Order controls", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Accept orders", fontWeight = FontWeight.SemiBold)
                                Text(
                                    if (acceptingOrders) "Customers can place new orders." else "Menu stays visible, but checkout is closed.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = acceptingOrders,
                                onCheckedChange = { acceptingOrders = it },
                                enabled = !state.isSaving
                            )
                        }
                        OutlinedTextField(
                            value = maxQtyText,
                            onValueChange = { if (it.matches(IntInput)) maxQtyText = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Maximum items per order") },
                            supportingText = { Text("Applies to the total quantity across all products.") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                        Button(
                            onClick = {
                                maxQtyText.toIntOrNull()?.let { vm.saveOrderControls(acceptingOrders, it) }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = maxQtyText.toIntOrNull() in 1..99 && !state.isSaving
                        ) {
                            if (state.isSaving) ButtonProgress() else Text("Save order controls")
                        }
                    }
                }
            }

            item("trips-title") {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Delivery trips", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    TextButton(onClick = {
                        val next = ((settings?.trips?.maxOfOrNull { it.tripNo } ?: 0) + 1).coerceAtMost(50)
                        tripDraft = TripDraft(tripNo = next.toString(), label = "Trip " + next)
                    }) { Text("＋ Add") }
                }
            }

            if (settings?.trips.isNullOrEmpty() && !state.isLoading) {
                item("no-trips") { MessageCard("No delivery trips found. Add one to allow checkout.", isError = true) }
            } else {
                items(settings?.trips.orEmpty(), key = { "trip-" + it.id }) { trip ->
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                        Column(modifier = Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(trip.label, fontWeight = FontWeight.Bold)
                                    Text(
                                        displayTime(trip.startTime) + " – " + displayTime(trip.endTime),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                StatusChip(if (trip.isOpen) "Open" else "Closed", if (trip.isOpen) Tone.Good else Tone.Neutral)
                            }
                            Text(
                                "Trip " + trip.tripNo + " · Customers can order until the trip ends.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedButton(
                                onClick = { tripDraft = TripDraft.from(trip) },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Edit trip") }
                        }
                    }
                }
            }

            item("buildings-title") {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Delivery buildings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    TextButton(onClick = {
                        val nextOrder = ((settings?.buildings?.maxOfOrNull { it.sortOrder } ?: 0) + 1)
                        buildingDraft = BuildingDraft(sortOrder = nextOrder.toString())
                    }) { Text("＋ Add") }
                }
            }

            if (settings?.buildings.isNullOrEmpty() && !state.isLoading) {
                item("no-buildings") { MessageCard("No delivery buildings found.", isError = true) }
            } else {
                items(settings?.buildings.orEmpty(), key = { "building-" + it.id }) { building ->
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                        Column(modifier = Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(building.code + " · " + building.name, fontWeight = FontWeight.Bold)
                                    Text(
                                        "Floors: " + building.floors.joinToString(", "),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                StatusChip(
                                    if (building.isActive) "Shown" else "Hidden",
                                    if (building.isActive) Tone.Good else Tone.Neutral
                                )
                            }
                            OutlinedButton(
                                onClick = { buildingDraft = BuildingDraft.from(building) },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Edit building") }
                        }
                    }
                }
            }
        }
    }

    tripDraft?.let { draft ->
        AlertDialog(
            onDismissRequest = { if (!state.isSaving) tripDraft = null },
            title = { Text(if (draft.id == null) "Add delivery trip" else "Edit delivery trip") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 470.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = draft.tripNo,
                        onValueChange = { v -> if (v.matches(IntInput)) tripDraft = draft.copy(tripNo = v) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Trip number") },
                        supportingText = { Text("Controls the order shown on the website.") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = draft.label,
                        onValueChange = { tripDraft = draft.copy(label = it.take(40)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Trip name") },
                        singleLine = true
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { pickTime(context, draft.startTime) { tripDraft = draft.copy(startTime = it) } },
                            modifier = Modifier.weight(1f)
                        ) { Text("Start\n" + displayTime(draft.startTime)) }
                        OutlinedButton(
                            onClick = { pickTime(context, draft.endTime) { tripDraft = draft.copy(endTime = it) } },
                            modifier = Modifier.weight(1f)
                        ) { Text("End\n" + displayTime(draft.endTime)) }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Open for orders", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Switch off to show this trip as closed by the team.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = draft.isOpen,
                            onCheckedChange = { tripDraft = draft.copy(isOpen = it) }
                        )
                    }
                    if (draft.endTime <= draft.startTime) {
                        MessageCard("End time must be later than start time.", isError = true)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { vm.saveTrip(draft) { tripDraft = null } },
                    enabled = draft.isValid && !state.isSaving
                ) {
                    if (state.isSaving) ButtonProgress() else Text("Save trip")
                }
            },
            dismissButton = {
                TextButton(onClick = { tripDraft = null }, enabled = !state.isSaving) { Text("Cancel") }
            }
        )
    }

    buildingDraft?.let { draft ->
        AlertDialog(
            onDismissRequest = { if (!state.isSaving) buildingDraft = null },
            title = { Text(if (draft.id == null) "Add building" else "Edit building") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 500.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = draft.code,
                        onValueChange = { buildingDraft = draft.copy(code = it.uppercase().take(8)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Short code") },
                        supportingText = { Text("Example: KKA or TS") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = draft.name,
                        onValueChange = { buildingDraft = draft.copy(name = it.take(80)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Building name") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = draft.floorsText,
                        onValueChange = { if (it.matches(FloorInput)) buildingDraft = draft.copy(floorsText = it) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Floors") },
                        supportingText = { Text("Type floor numbers separated by commas, e.g. 1, 2, 3, 4.") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = draft.sortOrder,
                        onValueChange = { v -> if (v.matches(IntInput)) buildingDraft = draft.copy(sortOrder = v) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Display order") },
                        supportingText = { Text("Lower numbers appear first.") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Show at checkout", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Hide it when you temporarily stop delivering there.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = draft.isActive,
                            onCheckedChange = { buildingDraft = draft.copy(isActive = it) }
                        )
                    }
                    if (draft.floors == null) {
                        MessageCard("Enter valid floor numbers without duplicates.", isError = true)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { vm.saveBuilding(draft) { buildingDraft = null } },
                    enabled = draft.isValid && !state.isSaving
                ) {
                    if (state.isSaving) ButtonProgress() else Text("Save building")
                }
            },
            dismissButton = {
                TextButton(onClick = { buildingDraft = null }, enabled = !state.isSaving) { Text("Cancel") }
            }
        )
    }
}
