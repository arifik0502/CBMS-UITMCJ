package com.crispyburger.admin.ui.common

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.crispyburger.admin.data.OrderStatus
import com.crispyburger.admin.data.PaymentStatus

/** Small spinner that fits inside a button. */
@Composable
fun ButtonProgress() {
    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { Button(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Back") } }
    )
}

@Composable
fun MessageCard(text: String, isError: Boolean, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isError) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(14.dp),
            color = if (isError) MaterialTheme.colorScheme.onErrorContainer
            else MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

@Composable
fun SectionHeader(
    title: String,
    actionLabel: String? = null,
    actionEnabled: Boolean = true,
    onAction: () -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (actionLabel != null) {
            OutlinedButton(onClick = onAction, enabled = actionEnabled) { Text(actionLabel) }
        }
    }
}

enum class Tone { Neutral, Good, Warn, Bad }

@Composable
fun StatusChip(text: String, tone: Tone, modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    val foreground = when (tone) {
        Tone.Good -> if (dark) Color(0xFF81C784) else Color(0xFF2E7D32)
        Tone.Warn -> if (dark) Color(0xFFFFB74D) else Color(0xFF9A5B00)
        Tone.Bad -> if (dark) Color(0xFFFF8A80) else Color(0xFFB3261E)
        Tone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = foreground.copy(alpha = 0.15f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = foreground
        )
    }
}

fun orderTone(status: String): Tone = when (status) {
    OrderStatus.DELIVERED -> Tone.Good
    OrderStatus.CANCELLED -> Tone.Bad
    OrderStatus.NEW, OrderStatus.CONFIRMED -> Tone.Warn
    else -> Tone.Neutral
}

fun paymentTone(status: String): Tone = when (status) {
    PaymentStatus.VERIFIED, PaymentStatus.CASH_COLLECTED -> Tone.Good
    PaymentStatus.REJECTED -> Tone.Bad
    PaymentStatus.PENDING_VERIFICATION, PaymentStatus.CASH_PENDING -> Tone.Warn
    else -> Tone.Neutral
}
