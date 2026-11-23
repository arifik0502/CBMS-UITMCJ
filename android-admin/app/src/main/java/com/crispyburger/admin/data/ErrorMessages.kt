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
                "Your admin account is not authorized to perform this action. Check your role and confirm the latest database migration has been applied."

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

            message.contains("sku_exists") ->
                "That SKU is already used by another product. Choose a different short code."

            message.contains("invalid_sku") ->
                "Use 2-20 letters, numbers, hyphens or underscores for the SKU."

            message.contains("invalid_product_name") ->
                "Product name must be 2-80 characters."

            message.contains("invalid_product_price") ->
                "Enter a valid product price."

            message.contains("invalid_product_image") ->
                "That product photo is not valid. Choose the photo again from your phone."

            message.contains("trip_no_exists") ->
                "That trip number is already used. Choose a different number."

            message.contains("invalid_trip_time") ->
                "Trip end time must be later than its start time."

            message.contains("building_code_exists") ->
                "That building code is already used."

            message.contains("invalid_floors") || message.contains("duplicate_floors") ->
                "Check the floor list. Use valid floor numbers without duplicates."

            message.contains("ticket_not_found") ->
                "This ticket could not be found. Refresh the list."

            message.contains("invalid_status") ->
                "That ticket status is not valid."

            message.contains("order_closed") ->
                "This order is cancelled or closed and cannot accept cash."

            message.contains("order_not_found") ->
                "This order could not be found. Refresh the order list."

            message.contains("pgrst202") || message.contains("could not find the function") ->
                "The website customization migration has not been applied in Supabase yet."

            message.contains("menu_required") ->
                "The menu must stay on the page so customers can order."

            message.contains("invalid_color") || message.contains("invalid_font") ->
                "One of the colours or the lettering is not valid."

            message.contains("invalid_link") ->
                "A link is not valid. Use a page from the list or a full https:// address."

            message.contains("invalid_image") ->
                "A picture is not valid. Choose it again from your phone."

            message.contains("too_many_custom") || message.contains("too_many_sections") ->
                "There are too many extra boxes on the page. Remove some."

            message.contains("no_draft") ->
                "There are no changes to publish."

            message.contains("version_not_found") ->
                "That earlier version could not be found."

            message.contains("permission denied") ->
                "Supabase denied this operation. Verify your active admin profile and database permissions."

            else -> fallback
        }
    }

    fun map(error: Throwable, fallback: String): String = map(describe(error), fallback)
}
