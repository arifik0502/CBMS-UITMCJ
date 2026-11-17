package com.crispyburger.admin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdminRealtimeEventClassifierTest {

    @Test
    fun paymentStatusChangeTakesPriorityOverOrderStatusChange() {
        val result = AdminRealtimeEventClassifier.orderUpdate(
            oldPaymentStatus = "PENDING_VERIFICATION",
            newPaymentStatus = "VERIFIED",
            oldOrderStatus = "NEW",
            newOrderStatus = "CONFIRMED"
        )

        assertEquals(AdminRealtimeNotice.PAYMENT_STATUS, result)
    }

    @Test
    fun orderStatusChangeIsClassified() {
        val result = AdminRealtimeEventClassifier.orderUpdate(
            oldPaymentStatus = "VERIFIED",
            newPaymentStatus = "VERIFIED",
            oldOrderStatus = "PREPARING",
            newOrderStatus = "READY"
        )

        assertEquals(AdminRealtimeNotice.ORDER_STATUS, result)
    }

    @Test
    fun unchangedOrderFieldsDoNotCreateNotice() {
        val result = AdminRealtimeEventClassifier.orderUpdate(
            oldPaymentStatus = "CASH_PENDING",
            newPaymentStatus = "CASH_PENDING",
            oldOrderStatus = "NEW",
            newOrderStatus = "NEW"
        )

        assertNull(result)
    }

    @Test
    fun receiptUploadIsDetectedOnlyWhenANewPathExists() {
        assertTrue(
            AdminRealtimeEventClassifier.paymentReceiptUpdated(
                oldReceiptPath = null,
                newReceiptPath = "orders/receipt-1.jpg"
            )
        )
        assertFalse(
            AdminRealtimeEventClassifier.paymentReceiptUpdated(
                oldReceiptPath = "orders/receipt-1.jpg",
                newReceiptPath = "orders/receipt-1.jpg"
            )
        )
        assertFalse(
            AdminRealtimeEventClassifier.paymentReceiptUpdated(
                oldReceiptPath = "orders/receipt-1.jpg",
                newReceiptPath = null
            )
        )
    }

    @Test
    fun physicalStockChangeIsDetected() {
        assertTrue(AdminRealtimeEventClassifier.physicalStockUpdated("10", "9"))
        assertFalse(AdminRealtimeEventClassifier.physicalStockUpdated("10", "10"))
    }
}
