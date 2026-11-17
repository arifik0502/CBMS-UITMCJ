package com.crispyburger.admin.data

internal object ProductValidation {
    fun normalizeSku(value: String): String? {
        val sku = value.trim().uppercase()
        return sku.takeIf { it.matches(Regex("^[A-Z0-9][A-Z0-9_-]{1,19}$")) }
    }

    fun priceCents(value: String): Int? {
        val text = value.trim()
        if (!text.matches(Regex("^\\d{1,5}(\\.\\d{1,2})?$"))) return null

        val parts = text.split(".")
        val whole = parts[0].toIntOrNull() ?: return null
        val fraction = if (parts.size == 2) parts[1].padEnd(2, '0') else "00"
        val cents = whole * 100 + fraction.toInt()

        return cents.takeIf { it in 1..1_000_000 }
    }

    fun formatPrice(cents: Int): String {
        val safe = cents.coerceAtLeast(0)
        return "${safe / 100}.${(safe % 100).toString().padStart(2, '0')}"
    }
}
