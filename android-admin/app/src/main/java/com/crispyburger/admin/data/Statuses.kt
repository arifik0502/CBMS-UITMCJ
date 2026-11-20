package com.crispyburger.admin.data

object OrderStatus {
    const val NEW = "NEW"
    const val CONFIRMED = "CONFIRMED"
    const val PREPARING = "PREPARING"
    const val READY = "READY"
    const val ASSIGNED = "ASSIGNED"
    const val OUT_FOR_DELIVERY = "OUT_FOR_DELIVERY"
    const val DELIVERED = "DELIVERED"
    const val CANCELLED = "CANCELLED"

    val closed: Set<String> = setOf(DELIVERED, CANCELLED)
    val incoming: Set<String> = setOf(NEW, CONFIRMED)
    val inProgress: Set<String> = setOf(PREPARING, READY, ASSIGNED, OUT_FOR_DELIVERY)

    fun isClosed(status: String): Boolean = status in closed

    /** Next office step. Legacy ASSIGNED orders behave like READY. */
    fun next(status: String): String? = when (status) {
        NEW -> CONFIRMED
        CONFIRMED -> PREPARING
        PREPARING -> READY
        READY, ASSIGNED -> OUT_FOR_DELIVERY
        OUT_FOR_DELIVERY -> DELIVERED
        else -> null
    }

    fun label(status: String): String = if (status == ASSIGNED) "Ready" else humanize(status)

    fun humanize(value: String): String =
        value.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
}

object PaymentStatus {
    const val PENDING_VERIFICATION = "PENDING_VERIFICATION"
    const val VERIFIED = "VERIFIED"
    const val REJECTED = "REJECTED"
    const val CASH_PENDING = "CASH_PENDING"
    const val CASH_COLLECTED = "CASH_COLLECTED"

    val settled: Set<String> = setOf(VERIFIED, CASH_COLLECTED)

    fun label(status: String): String = OrderStatus.humanize(status)
}

object PaymentMethod {
    const val CASH = "CASH"
    const val DUITNOW = "DUITNOW"

    fun label(method: String): String = when (method) {
        DUITNOW -> "DuitNow"
        CASH -> "Cash"
        else -> OrderStatus.humanize(method)
    }
}

object OrderRules {
    fun awaitingVerification(order: AdminOrder): Boolean =
        order.paymentStatus == PaymentStatus.PENDING_VERIFICATION

    fun canCollectCash(order: AdminOrder): Boolean =
        order.paymentStatus == PaymentStatus.CASH_PENDING &&
            order.orderStatus in setOf(OrderStatus.OUT_FOR_DELIVERY, OrderStatus.DELIVERED)

    fun nextStatus(order: AdminOrder): String? = OrderStatus.next(order.orderStatus)

    fun deliveryBlockedByPayment(order: AdminOrder): Boolean =
        OrderStatus.next(order.orderStatus) == OrderStatus.DELIVERED &&
            order.paymentMethod == PaymentMethod.DUITNOW &&
            order.paymentStatus != PaymentStatus.VERIFIED

    fun canAdvance(order: AdminOrder): Boolean =
        nextStatus(order) != null && !deliveryBlockedByPayment(order)

    fun canCancel(order: AdminOrder): Boolean = !OrderStatus.isClosed(order.orderStatus)
}
