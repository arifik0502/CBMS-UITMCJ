package com.crispyburger.admin.data

import java.time.LocalDate

object ReportCsv {
    fun escape(value: String): String {
        // Neutralise spreadsheet formula injection, then quote when needed.
        val safe = if (value.isNotEmpty() && value[0] in charArrayOf('=', '+', '-', '@')) "'$value" else value
        return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + safe.replace("\"", "\"\"") + "\""
        } else {
            safe
        }
    }

    fun build(
        from: LocalDate,
        to: LocalDate,
        report: AdminSalesReport,
        delivered: List<AdminSalesOrder>
    ): String {
        val s = report.summary
        val lines = mutableListOf<String>()
        lines += "CBMS sales report,$from,$to"
        lines += "Delivered orders,${s.deliveredOrderCount},${Money.plain(s.deliveredValueCents)}"
        lines += "Settled orders,${s.settledOrderCount},${Money.plain(s.settledRevenueCents)}"
        lines += "Outstanding orders,${s.outstandingOrderCount},${Money.plain(s.outstandingValueCents)}"
        lines += "Cash collected,${report.cashCollectionCount},${Money.plain(report.cashCollectedCents)}"
        lines += ""
        lines += "order_no,delivered_at_myt,payment_method,payment_status,total_rm"
        delivered.forEach {
            lines += listOf(
                escape(it.orderNo),
                escape(Formats.malaysiaDateTime(it.deliveredAt)),
                escape(it.paymentMethod),
                escape(it.paymentStatus),
                Money.plain(it.totalCents.toLong())
            ).joinToString(",")
        }
        return lines.joinToString("\n")
    }
}
