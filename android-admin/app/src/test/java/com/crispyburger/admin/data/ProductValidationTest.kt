package com.crispyburger.admin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProductValidationTest {
    @Test
    fun normalizesSkuToUppercase() {
        assertEquals("BURGER_01", ProductValidation.normalizeSku(" burger_01 "))
    }

    @Test
    fun rejectsInvalidSku() {
        assertNull(ProductValidation.normalizeSku("A"))
        assertNull(ProductValidation.normalizeSku("BURGER 01"))
        assertNull(ProductValidation.normalizeSku("A".repeat(21)))
    }

    @Test
    fun parsesPricesWithoutFloatingPoint() {
        assertEquals(650, ProductValidation.priceCents("6.50"))
        assertEquals(650, ProductValidation.priceCents("6.5"))
        assertEquals(600, ProductValidation.priceCents("6"))
    }

    @Test
    fun rejectsInvalidPrices() {
        assertNull(ProductValidation.priceCents(""))
        assertNull(ProductValidation.priceCents("0"))
        assertNull(ProductValidation.priceCents("-1.00"))
        assertNull(ProductValidation.priceCents("1.999"))
        assertNull(ProductValidation.priceCents("10001"))
    }

    @Test
    fun formatsPriceFromIntegerCents() {
        assertEquals("6.50", ProductValidation.formatPrice(650))
        assertEquals("0.01", ProductValidation.formatPrice(1))
    }
}
