package com.crispyburger.admin.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportCsvTest {
    @Test fun escapesCommasQuotesAndFormulas() {
        assertEquals("plain", ReportCsv.escape("plain"))
        assertEquals("\"a,b\"", ReportCsv.escape("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", ReportCsv.escape("say \"hi\""))
        assertEquals("'=SUM(A1)", ReportCsv.escape("=SUM(A1)"))
        assertEquals("'-1", ReportCsv.escape("-1"))
    }

    @Test fun buildsSummaryAndRows() {
        val orders = listOf(
            AdminSalesOrder("1", "CB-0001", PaymentStatus.CASH_COLLECTED, PaymentMethod.CASH, 650, "2026-11-18T16:30:00+00:00")
        )
        val report = AdminSalesReport(
            summary = SalesReportCalculator.calculate(orders),
            recentOrders = orders,
            cashCollections = emptyList(),
            cashCollectedCents = 650,
            cashCollectionCount = 1,
            approximate = false
        )
        val csv = ReportCsv.build(LocalDate.of(2026, 11, 13), LocalDate.of(2026, 11, 19), report, orders)
        val lines = csv.lines()
        assertEquals("CBMS sales report,2026-11-13,2026-11-19", lines[0])
        assertEquals("Delivered orders,1,6.50", lines[1])
        assertEquals("Cash collected,1,6.50", lines[4])
        assertTrue(lines.last().startsWith("CB-0001,\"19 Nov 2026, 12:30 AM\",CASH,CASH_COLLECTED,6.50"))
    }
}
