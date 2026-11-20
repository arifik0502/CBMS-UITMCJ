package com.crispyburger.admin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderRulesTest {
    @Test fun statusChain() {
        assertEquals(OrderStatus.CONFIRMED, OrderStatus.next(OrderStatus.NEW))
        assertEquals(OrderStatus.PREPARING, OrderStatus.next(OrderStatus.CONFIRMED))
        assertEquals(OrderStatus.READY, OrderStatus.next(OrderStatus.PREPARING))
        assertEquals(OrderStatus.OUT_FOR_DELIVERY, OrderStatus.next(OrderStatus.READY))
        assertEquals(OrderStatus.OUT_FOR_DELIVERY, OrderStatus.next(OrderStatus.ASSIGNED))
        assertEquals(OrderStatus.DELIVERED, OrderStatus.next(OrderStatus.OUT_FOR_DELIVERY))
        assertNull(OrderStatus.next(OrderStatus.DELIVERED))
        assertNull(OrderStatus.next(OrderStatus.CANCELLED))
    }

    @Test fun labels() {
        assertEquals("Ready", OrderStatus.label(OrderStatus.ASSIGNED))
        assertEquals("Out for delivery", OrderStatus.label(OrderStatus.OUT_FOR_DELIVERY))
        assertEquals("Pending verification", PaymentStatus.label(PaymentStatus.PENDING_VERIFICATION))
        assertEquals("DuitNow", PaymentMethod.label(PaymentMethod.DUITNOW))
    }

    @Test fun cashCollectionOnlyWhenOutForDeliveryOrDelivered() {
        val pending = testOrder(orderStatus = OrderStatus.PREPARING)
        val out = testOrder(orderStatus = OrderStatus.OUT_FOR_DELIVERY)
        val cancelled = testOrder(orderStatus = OrderStatus.CANCELLED)
        val collected = testOrder(orderStatus = OrderStatus.DELIVERED, paymentStatus = PaymentStatus.CASH_COLLECTED)
        assertFalse(OrderRules.canCollectCash(pending))
        assertTrue(OrderRules.canCollectCash(out))
        assertFalse(OrderRules.canCollectCash(cancelled))
        assertFalse(OrderRules.canCollectCash(collected))
    }

    @Test fun duitNowCannotBeDeliveredUntilVerified() {
        val unverified = testOrder(
            orderStatus = OrderStatus.OUT_FOR_DELIVERY,
            paymentMethod = PaymentMethod.DUITNOW,
            paymentStatus = PaymentStatus.PENDING_VERIFICATION
        )
        val verified = unverified.copy(paymentStatus = PaymentStatus.VERIFIED)
        assertTrue(OrderRules.deliveryBlockedByPayment(unverified))
        assertFalse(OrderRules.canAdvance(unverified))
        assertFalse(OrderRules.deliveryBlockedByPayment(verified))
        assertTrue(OrderRules.canAdvance(verified))
    }

    @Test fun earlierStepsAreNotBlockedByPayment() {
        val preparing = testOrder(
            orderStatus = OrderStatus.PREPARING,
            paymentMethod = PaymentMethod.DUITNOW,
            paymentStatus = PaymentStatus.PENDING_VERIFICATION
        )
        assertTrue(OrderRules.canAdvance(preparing))
    }

    @Test fun closedOrdersCannotBeCancelled() {
        assertFalse(OrderRules.canCancel(testOrder(orderStatus = OrderStatus.DELIVERED)))
        assertFalse(OrderRules.canCancel(testOrder(orderStatus = OrderStatus.CANCELLED)))
        assertTrue(OrderRules.canCancel(testOrder(orderStatus = OrderStatus.READY)))
    }

    @Test fun countsFromLoadedOrders() {
        val today = java.time.LocalDate.of(2026, 11, 19)
        val orders = listOf(
            testOrder(id = "1", orderStatus = OrderStatus.NEW, paymentStatus = PaymentStatus.PENDING_VERIFICATION),
            testOrder(id = "2", orderStatus = OrderStatus.CONFIRMED),
            testOrder(id = "3", orderStatus = OrderStatus.OUT_FOR_DELIVERY),
            testOrder(id = "4", orderStatus = OrderStatus.DELIVERED, deliveredAt = "2026-11-19T02:00:00+00:00"),
            testOrder(id = "5", orderStatus = OrderStatus.DELIVERED, deliveredAt = "2026-11-17T02:00:00+00:00")
        )
        val counts = AdminOrderCounts.fromOrders(orders, today)
        assertEquals(1, counts.paymentReview)
        assertEquals(2, counts.newOrders)
        assertEquals(1, counts.inProgress)
        assertEquals(1, counts.deliveredToday)
    }
}
