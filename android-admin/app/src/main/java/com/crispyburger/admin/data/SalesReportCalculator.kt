package com.crispyburger.admin.data

data class SalesReportSummary(
    val deliveredOrderCount: Int,
    val settledOrderCount: Int,
    val outstandingOrderCount: Int,
    val deliveredValueCents: Long,
    val settledRevenueCents: Long,
    val outstandingValueCents: Long
)

object SalesReportCalculator {
    private val settledPaymentStatuses = setOf("VERIFIED", "CASH_COLLECTED")

    fun calculate(orders: List<AdminSalesOrder>): SalesReportSummary {
        val settledOrders = orders.filter { it.paymentStatus in settledPaymentStatuses }
        val deliveredValue = orders.sumOf { it.totalCents.toLong() }
        val settledRevenue = settledOrders.sumOf { it.totalCents.toLong() }

        return SalesReportSummary(
            deliveredOrderCount = orders.size,
            settledOrderCount = settledOrders.size,
            outstandingOrderCount = orders.size - settledOrders.size,
            deliveredValueCents = deliveredValue,
            settledRevenueCents = settledRevenue,
            outstandingValueCents = deliveredValue - settledRevenue
        )
    }
}
