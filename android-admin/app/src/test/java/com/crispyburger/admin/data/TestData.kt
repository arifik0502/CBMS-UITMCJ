package com.crispyburger.admin.data

fun testOrder(
    id: String = "o1",
    orderNo: String = "CB-0001",
    orderStatus: String = OrderStatus.NEW,
    paymentStatus: String = PaymentStatus.CASH_PENDING,
    paymentMethod: String = PaymentMethod.CASH,
    tripId: String? = null,
    tripDate: String? = null,
    tripLabel: String = "No trip assigned",
    tripNo: Int = 0,
    buildingLabel: String = "A - Block A",
    floor: Int = 1,
    room: String = "101",
    deliveredAt: String? = null,
    customerName: String = "Aina",
    customerPhone: String = "0123456789",
    total: Int = 650
) = AdminOrder(
    id = id,
    orderNo = orderNo,
    customerName = customerName,
    customerPhone = customerPhone,
    buildingId = "b1",
    floorNumber = floor,
    roomNumber = room,
    paymentMethod = paymentMethod,
    orderStatus = orderStatus,
    paymentStatus = paymentStatus,
    totalCents = total,
    createdAt = "2026-11-19T10:00:00+00:00",
    deliveredAt = deliveredAt,
    tripId = tripId,
    tripDate = tripDate,
    buildingLabel = buildingLabel,
    tripLabel = tripLabel,
    tripNo = tripNo
)
