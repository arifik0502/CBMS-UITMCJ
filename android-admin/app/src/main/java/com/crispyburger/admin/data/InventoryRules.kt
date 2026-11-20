package com.crispyburger.admin.data

object InventoryRules {
    const val LOW_STOCK_THRESHOLD = 5

    const val RESTOCK = "RESTOCK"
    const val REDUCTION = "REDUCTION"
    const val SET = "SET"

    fun resultingPhysical(type: String, current: Int, quantity: Int?): Int? {
        if (quantity == null) return null
        return when (type) {
            RESTOCK -> current + quantity
            REDUCTION -> current - quantity
            SET -> quantity
            else -> null
        }
    }

    fun isValidQuantity(type: String, quantity: Int?): Boolean =
        quantity != null && quantity >= 0 && (type == SET || quantity > 0)

    fun meetsReservation(resulting: Int?, reserved: Int): Boolean =
        resulting != null && resulting >= reserved

    fun isLow(available: Int): Boolean = available <= LOW_STOCK_THRESHOLD
}
