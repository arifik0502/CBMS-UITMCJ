package com.crispyburger.admin.data

enum class OrderFilter(val label: String) {
    ACTIVE("Active"),
    PAYMENT("Payment review"),
    TRIPS("Delivery plan"),
    ARCHIVE("Archive")
}

data class TripGroup(val key: String, val title: String, val orders: List<AdminOrder>)

object OrderFilters {
    fun apply(orders: List<AdminOrder>, search: String, filter: OrderFilter): List<AdminOrder> {
        val query = search.trim()
        return orders.filter { order ->
            val matchesSearch = query.isEmpty() ||
                order.orderNo.contains(query, ignoreCase = true) ||
                order.customerName.contains(query, ignoreCase = true) ||
                order.customerPhone.contains(query, ignoreCase = true)
            val matchesStatus = when (filter) {
                OrderFilter.PAYMENT -> order.paymentStatus == PaymentStatus.PENDING_VERIFICATION
                OrderFilter.ACTIVE, OrderFilter.TRIPS -> !OrderStatus.isClosed(order.orderStatus)
                OrderFilter.ARCHIVE -> OrderStatus.isClosed(order.orderStatus)
            }
            matchesSearch && matchesStatus
        }
    }

    /** Groups open orders by delivery trip so the owner can plan runs building by building. */
    fun groupByTrip(orders: List<AdminOrder>): List<TripGroup> =
        orders.groupBy { "${it.tripDate.orEmpty()}|${it.tripId.orEmpty()}" }
            .map { (key, list) ->
                val sorted = list.sortedWith(
                    compareBy<AdminOrder>({ it.buildingLabel }, { it.floorNumber }, { it.roomNumber })
                )
                val first = sorted.first()
                val title = buildString {
                    if (!first.tripDate.isNullOrBlank()) append(first.tripDate).append(" · ")
                    append(first.tripLabel)
                    if (first.tripWindow.isNotBlank()) append(" · ").append(first.tripWindow)
                    append(" · ").append(sorted.size).append(if (sorted.size == 1) " order" else " orders")
                }
                TripGroup(key, title, sorted)
            }
            .sortedWith(
                compareBy<TripGroup>(
                    { it.orders.first().tripId == null },
                    { it.orders.first().tripDate.orEmpty() },
                    { it.orders.first().tripNo },
                    { it.key }
                )
            )
}
