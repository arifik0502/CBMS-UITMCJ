package com.crispyburger.admin.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.crispyburger.admin.data.DEVELOPER_EMAIL
import com.crispyburger.admin.data.DeveloperProfile

@Composable
fun DeveloperHomeScreen(
    profile: DeveloperProfile,
    onSignOut: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text("CBMS ADMIN", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Developer account",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.secondary
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    profile.fullName.ifBlank { "Developer" },
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(DEVELOPER_EMAIL, style = MaterialTheme.typography.bodyMedium)
                Text("Role: OWNER", style = MaterialTheme.typography.labelLarge)
                Text(
                    "Developer-only access is active.",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Phase 5 workspace", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Authentication is connected to Supabase. Order, payment, inventory, delivery and reporting modules will be enabled after their individual data flows and permissions pass testing.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Staff account creation is disabled. No ADMIN or SELLER account can sign in to this app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Button(
            onClick = onSignOut,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Sign out")
        }
    }
}
