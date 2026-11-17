package com.crispyburger.admin.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SalesReportCalculatorTest {

    @Test
    fun emptyReportReturnsZeroTotals() {
        val result = SalesReportCalculator.calculate(emptyList())

        assertEquals(0, result.deliveredOrderCount)
        assertEquals(0, result.settledOrderCount)
        assertEquals(0, result.outstandingOrderCount)
        assertEquals(0L, result.deliveredValueCents)
        assertEquals(0L, result.settledRevenueCents)
        assertEquals(0L, result.outstandingValueCents)
    }

    @Test
    fun onlyVerifiedAndCashCollectedPaymentsCountAsSettledRevenue() {
        val orders = listOf(
            salesOrder("A", "VERIFIED", 650),
            salesOrder("B", "CASH_COLLECTED", 650),
            salesOrder("C", "CASH_PENDING", 650),
            salesOrder("D", "PENDING_VERIFICATION", 650)
        )

        val result = SalesReportCalculator.calculate(orders)

        assertEquals(4, result.deliveredOrderCount)
        assertEquals(2, result.settledOrderCount)
        assertEquals(2, result.outstandingOrderCount)
        assertEquals(2600L, result.deliveredValueCents)
        assertEquals(1300L, result.settledRevenueCents)
        assertEquals(1300L, result.outstandingValueCents)
    }

    @Test
    fun largeTotalsUseLongArithmetic() {
        val orders = listOf(
            salesOrder("A", "VERIFIED", Int.MAX_VALUE),
            salesOrder("B", "CASH_COLLECTED", Int.MAX_VALUE),
            salesOrder("C", "CASH_PENDING", Int.MAX_VALUE)
        )

        val result = SalesReportCalculator.calculate(orders)

        assertEquals(Int.MAX_VALUE.toLong() * 3L, result.deliveredValueCents)
        assertEquals(Int.MAX_VALUE.toLong() * 2L, result.settledRevenueCents)
        assertEquals(Int.MAX_VALUE.toLong(), result.outstandingValueCents)
    }

    private fun salesOrder(
        orderNo: String,
        paymentStatus: String,
        totalCents: Int
    ) = AdminSalesOrder(
        id = orderNo,
        orderNo = orderNo,
        paymentStatus = paymentStatus,
        paymentMethod = if (paymentStatus == "CASH_COLLECTED" || paymentStatus == "CASH_PENDING") "CASH" else "DUITNOW",
        totalCents = totalCents,
        deliveredAt = "2026-11-16T10:00:00Z"
    )
}
