package com.crispyburger.admin.data

enum class AdminRealtimeNotice {
    PAYMENT_STATUS,
    ORDER_STATUS
}

object AdminRealtimeEventClassifier {
    fun orderUpdate(
        oldPaymentStatus: String?,
        newPaymentStatus: String?,
        oldOrderStatus: String?,
        newOrderStatus: String?
    ): AdminRealtimeNotice? {
        if (oldPaymentStatus != newPaymentStatus) {
            return AdminRealtimeNotice.PAYMENT_STATUS
        }
        if (oldOrderStatus != newOrderStatus) {
            return AdminRealtimeNotice.ORDER_STATUS
        }
        return null
    }

    fun paymentReceiptUpdated(
        oldReceiptPath: String?,
        newReceiptPath: String?
    ): Boolean {
        return !newReceiptPath.isNullOrBlank() && oldReceiptPath != newReceiptPath
    }

    fun physicalStockUpdated(
        oldPhysicalStock: String?,
        newPhysicalStock: String?
    ): Boolean {
        return oldPhysicalStock != newPhysicalStock
    }

    /**
     * True only when available stock newly drops to or below the threshold.
     * Needs the previous value (table replica identity FULL); otherwise stays quiet.
     */
    fun crossedLowStock(
        oldAvailable: Int?,
        newAvailable: Int?,
        threshold: Int = InventoryRules.LOW_STOCK_THRESHOLD
    ): Boolean =
        oldAvailable != null && newAvailable != null &&
            newAvailable <= threshold && oldAvailable > threshold
}
