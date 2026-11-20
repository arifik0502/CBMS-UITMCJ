package com.crispyburger.admin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InventoryRulesTest {
    @Test fun resultingStock() {
        assertEquals(15, InventoryRules.resultingPhysical(InventoryRules.RESTOCK, 10, 5))
        assertEquals(5, InventoryRules.resultingPhysical(InventoryRules.REDUCTION, 10, 5))
        assertEquals(3, InventoryRules.resultingPhysical(InventoryRules.SET, 10, 3))
        assertNull(InventoryRules.resultingPhysical(InventoryRules.SET, 10, null))
        assertNull(InventoryRules.resultingPhysical("BOGUS", 10, 1))
    }

    @Test fun quantityValidation() {
        assertFalse(InventoryRules.isValidQuantity(InventoryRules.RESTOCK, 0))
        assertTrue(InventoryRules.isValidQuantity(InventoryRules.SET, 0))
        assertFalse(InventoryRules.isValidQuantity(InventoryRules.SET, null))
        assertTrue(InventoryRules.isValidQuantity(InventoryRules.REDUCTION, 1))
    }

    @Test fun reservationGuard() {
        assertTrue(InventoryRules.meetsReservation(5, 5))
        assertFalse(InventoryRules.meetsReservation(4, 5))
        assertFalse(InventoryRules.meetsReservation(null, 0))
    }

    @Test fun lowStockThreshold() {
        assertTrue(InventoryRules.isLow(5))
        assertFalse(InventoryRules.isLow(6))
    }

    @Test fun lowStockCrossingNotifiesOnlyOnTheWayDown() {
        assertTrue(AdminRealtimeEventClassifier.crossedLowStock(oldAvailable = 8, newAvailable = 5))
        assertFalse(AdminRealtimeEventClassifier.crossedLowStock(oldAvailable = 5, newAvailable = 3))
        assertFalse(AdminRealtimeEventClassifier.crossedLowStock(oldAvailable = 3, newAvailable = 20))
        assertFalse(AdminRealtimeEventClassifier.crossedLowStock(oldAvailable = null, newAvailable = 1))
    }
}
