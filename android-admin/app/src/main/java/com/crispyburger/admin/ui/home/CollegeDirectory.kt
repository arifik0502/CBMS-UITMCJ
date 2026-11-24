package com.crispyburger.admin.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class CollegeZone(val zone: String, val residents: String, val colleges: List<String>)

@Composable
internal fun CollegeDirectory() {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Senarai kolej kediaman UiTM Segamat", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Tutup senarai" else "Lihat senarai kolej")
            }
            if (expanded) {
                val assets = LocalContext.current.assets
                val zones = remember(assets) {
                    assets.open("segamat-colleges.json").bufferedReader().use {
                        Json.decodeFromString<List<CollegeZone>>(it.readText())
                    }
                }
                Text("UiTM Cawangan Johor, Kampus Segamat", style = MaterialTheme.typography.bodySmall)
                zones.forEach { group ->
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(group.zone, modifier = Modifier.semantics { heading() }, fontWeight = FontWeight.Bold)
                        Text(group.residents, style = MaterialTheme.typography.bodySmall)
                        group.colleges.forEach { college -> Text("• $college") }
                    }
                }
            }
        }
    }
}
