package com.crispyburger.admin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderFiltersTest {
    private val orders = listOf(
        testOrder(id = "1", orderNo = "CB-0001", orderStatus = OrderStatus.NEW, customerName = "Aina"),
        testOrder(id = "2", orderNo = "CB-0002", orderStatus = OrderStatus.DELIVERED, customerName = "Budi"),
        testOrder(
            id = "3", orderNo = "CB-0003", orderStatus = OrderStatus.PREPARING,
            paymentStatus = PaymentStatus.PENDING_VERIFICATION, customerName = "Citra"
        ),
        testOrder(id = "4", orderNo = "CB-0004", orderStatus = OrderStatus.CANCELLED)
    )

    @Test fun activeHidesClosedOrders() {
        assertEquals(listOf("1", "3"), OrderFilters.apply(orders, "", OrderFilter.ACTIVE).map { it.id })
    }

    @Test fun archiveShowsOnlyClosedOrders() {
        assertEquals(listOf("2", "4"), OrderFilters.apply(orders, "", OrderFilter.ARCHIVE).map { it.id })
    }

    @Test fun paymentReviewFiltersByPaymentStatus() {
        assertEquals(listOf("3"), OrderFilters.apply(orders, "", OrderFilter.PAYMENT).map { it.id })
    }

    @Test fun searchMatchesNameNumberAndPhoneCaseInsensitively() {
        assertEquals(listOf("1"), OrderFilters.apply(orders, "aina", OrderFilter.ACTIVE).map { it.id })
        assertEquals(listOf("3"), OrderFilters.apply(orders, "0003", OrderFilter.ACTIVE).map { it.id })
        assertEquals(listOf("1", "3"), OrderFilters.apply(orders, "0123", OrderFilter.ACTIVE).map { it.id })
    }

    @Test fun groupsOpenOrdersByTripWithUnassignedLast() {
        val list = listOf(
            testOrder(id = "a", tripId = "t2", tripDate = "2026-11-20", tripLabel = "Trip 2", tripNo = 2, buildingLabel = "B"),
            testOrder(id = "b", tripId = "t1", tripDate = "2026-11-20", tripLabel = "Trip 1", tripNo = 1, buildingLabel = "B", floor = 2),
            testOrder(id = "c", tripId = "t1", tripDate = "2026-11-20", tripLabel = "Trip 1", tripNo = 1, buildingLabel = "A", floor = 5),
            testOrder(id = "d")
        )
        val groups = OrderFilters.groupByTrip(list)
        assertEquals(3, groups.size)
        assertEquals(listOf("c", "b"), groups[0].orders.map { it.id })
        assertTrue(groups[0].title.contains("Trip 1"))
        assertTrue(groups[0].title.endsWith("2 orders"))
        assertEquals(listOf("a"), groups[1].orders.map { it.id })
        assertEquals(listOf("d"), groups[2].orders.map { it.id })
        assertTrue(groups[2].title.endsWith("1 order"))
    }
}
