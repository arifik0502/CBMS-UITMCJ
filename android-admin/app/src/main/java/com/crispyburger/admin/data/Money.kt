package com.crispyburger.admin.data

object Money {
    /** "RM 6.50" from integer cents. Negative values clamp to zero. */
    fun format(cents: Long): String = "RM ${plain(cents)}"

    fun format(cents: Int): String = format(cents.toLong())

    /** "6.50" (no currency prefix), used for CSV and inputs. */
    fun plain(cents: Long): String {
        val safe = cents.coerceAtLeast(0L)
        return "${safe / 100}.${(safe % 100).toString().padStart(2, '0')}"
    }
}
