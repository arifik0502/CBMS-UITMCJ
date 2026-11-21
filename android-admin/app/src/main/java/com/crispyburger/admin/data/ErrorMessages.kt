package com.crispyburger.admin.data

object ErrorMessages {
    fun describe(error: Throwable): String =
        generateSequence(error as Throwable?) { it.cause }
            .mapNotNull { it.message }
            .joinToString(" ")

    fun map(rawMessage: String, fallback: String): String {
        val message = rawMessage.lowercase()
        return when {
            message.contains("unable to resolve host") ||
                message.contains("failed to connect") ||
                message.contains("timeout") || message.contains("timed out") ->
                "No connection to the server. Check your internet and try again."

            message.contains("trip_date") &&
                (message.contains("does not exist") || message.contains("42703")) ->
                "The Supabase database is missing the CBMS trips migration. Check the trips migration, then refresh."

            message.contains("does not exist") &&
                (message.contains("column") || message.contains("relation")) ->
                "The Supabase database schema is behind this app. Check that all required migrations have been applied."

            message.contains("forbidden") || message.contains("42501") ->
                "Your developer account is not authorized to perform this action. Confirm that its OWNER profile is active and that the latest database migration has been applied."

            message.contains("invalid_payment_state") ->
                "This order is no longer awaiting cash collection. Refresh the order and check its payment status."

            message.contains("payment_not_verified") ->
                "Verify the DuitNow payment before marking this order delivered."

            message.contains("invalid_transition") ->
                "That status change is not allowed from the order's current state. Refresh and try again."

            message.contains("below_reserved") ->
                "Inventory adjustment rejected: the database says physical stock would fall below reserved stock. Refresh inventory. If Restock is failing while physical stock is already above reserved stock, the live adjust_inventory function or its adjustment-type mapping needs to be checked."

            message.contains("not_out_for_delivery") ->
                "The live collect_cash function still has the old delivery restriction. Apply the latest cash-payment migration in Supabase, then retry."

            message.contains("ticket_not_found") ->
                "This ticket could not be found. Refresh the list."

            message.contains("invalid_status") ->
                "That ticket status is not valid."

            message.contains("order_closed") ->
                "This order is cancelled or closed and cannot accept cash."

            message.contains("order_not_found") ->
                "This order could not be found. Refresh the order list."

            message.contains("permission denied") ->
                "Supabase denied this operation. Verify the active developer OWNER account and database permissions."

            else -> fallback
        }
    }

    fun map(error: Throwable, fallback: String): String = map(describe(error), fallback)
}
