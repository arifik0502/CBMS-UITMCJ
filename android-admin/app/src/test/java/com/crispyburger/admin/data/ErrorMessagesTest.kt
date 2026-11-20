package com.crispyburger.admin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorMessagesTest {
    private val fallback = "fallback"

    @Test fun unknownMessageUsesFallback() {
        assertEquals(fallback, ErrorMessages.map("something odd", fallback))
    }

    @Test fun forbiddenMapsToAuthorization() {
        assertTrue(ErrorMessages.map("FORBIDDEN", fallback).contains("not authorized"))
        assertTrue(ErrorMessages.map("code 42501", fallback).contains("not authorized"))
    }

    @Test fun networkErrorsAreFriendly() {
        assertTrue(ErrorMessages.map("Unable to resolve host x.supabase.co", fallback).startsWith("No connection"))
        assertTrue(ErrorMessages.map("Request timeout has expired", fallback).startsWith("No connection"))
    }

    @Test fun tripMigrationBeatsGenericSchemaMessage() {
        val msg = ErrorMessages.map("column orders.trip_date does not exist 42703", fallback)
        assertTrue(msg.contains("trips migration"))
    }

    @Test fun businessRuleCodesMap() {
        assertTrue(ErrorMessages.map("payment_not_verified", fallback).contains("Verify the DuitNow"))
        assertTrue(ErrorMessages.map("invalid_transition", fallback).contains("not allowed"))
        assertTrue(ErrorMessages.map("order_closed", fallback).contains("closed"))
        assertTrue(ErrorMessages.map("below_reserved", fallback).contains("reserved"))
    }

    @Test fun describeWalksCauseChain() {
        val error = RuntimeException("outer", IllegalStateException("inner forbidden"))
        assertTrue(ErrorMessages.describe(error).contains("inner forbidden"))
        assertTrue(ErrorMessages.map(error, fallback).contains("not authorized"))
    }
}
