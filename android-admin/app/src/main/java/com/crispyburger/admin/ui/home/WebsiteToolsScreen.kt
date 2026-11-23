package com.crispyburger.admin.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun WebsiteToolsScreen(
    siteVm: SiteViewModel,
    deliveryVm: DeliverySettingsViewModel
) {
    var section by rememberSaveable { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = section == 0,
                onClick = { section = 0 },
                label = { Text("Design") },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = section == 1,
                onClick = { section = 1 },
                label = { Text("Delivery & orders") },
                modifier = Modifier.weight(1f)
            )
        }

        when (section) {
            0 -> SiteScreen(siteVm)
            else -> DeliverySettingsScreen(deliveryVm)
        }
    }
}
