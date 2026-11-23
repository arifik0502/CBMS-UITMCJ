package com.crispyburger.admin.ui.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeliverySettingsValidationTest {
    @Test
    fun sameDayTripIsValid() {
        assertTrue(
            TripDraft(
                tripNo = "1",
                label = "Trip 1",
                startTime = "19:00:00",
                endTime = "19:30:00"
            ).isValid
        )
    }

    @Test
    fun overnightTripIsValid() {
        assertTrue(
            TripDraft(
                tripNo = "3",
                label = "Trip 3",
                startTime = "23:00:00",
                endTime = "02:15:00"
            ).isValid
        )
    }

    @Test
    fun identicalStartAndEndIsInvalid() {
        assertFalse(
            TripDraft(
                tripNo = "3",
                label = "Trip 3",
                startTime = "23:00:00",
                endTime = "23:00:00"
            ).isValid
        )
    }
}
