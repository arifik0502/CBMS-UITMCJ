package com.crispyburger.admin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

fun testTicket(
    id: String = "t1",
    no: String = "TK-0001",
    status: String = TicketStatus.OPEN,
    message: String = "My burger was cold",
    order: String? = null,
    photo: String? = null
) = AdminTicket(
    id = id, ticketNo = no, message = message, customerName = "Aina", customerPhone = "0123456789",
    attachmentPath = photo, status = status, createdAt = "2026-11-21T10:00:00+00:00",
    orders = order?.let { AdminTicketOrder(it) }
)

class TicketRulesTest {
    private val tickets = listOf(
        testTicket("1", "TK-0001", TicketStatus.OPEN),
        testTicket("2", "TK-0002", TicketStatus.RESOLVED),
        testTicket("3", "TK-0003", TicketStatus.OPEN)
    )

    @Test fun filtersByStatus() {
        assertEquals(listOf("1", "3"), TicketRules.filter(tickets, TicketFilter.OPEN).map { it.id })
        assertEquals(listOf("2"), TicketRules.filter(tickets, TicketFilter.RESOLVED).map { it.id })
        assertEquals(2, TicketRules.openCount(tickets))
    }

    @Test fun toggleSwitchesBetweenOpenAndResolved() {
        assertEquals(TicketStatus.RESOLVED, TicketRules.toggled(TicketStatus.OPEN))
        assertEquals(TicketStatus.OPEN, TicketRules.toggled(TicketStatus.RESOLVED))
    }

    @Test fun previewUsesFirstNonEmptyLineAndShortens() {
        assertEquals("Wrong item", TicketRules.preview("\n  \n  Wrong item  \nsecond line"))
        val long = "x".repeat(200)
        val p = TicketRules.preview(long, 50)
        assertEquals(50, p.length)
        assertEquals("…", p.takeLast(1))
        assertEquals("", TicketRules.preview("   "))
    }

    @Test fun dialableKeepsDigitsAndPlus() {
        assertEquals("0123456789", TicketRules.dialable("012-345 6789"))
        assertEquals("+60123456789", TicketRules.dialable("+60 12-345 6789"))
        assertNull(TicketRules.dialable("123"))
        assertNull(TicketRules.dialable("not a number"))
    }

    @Test fun orderNumberIsNullForGeneralTickets() {
        assertNull(testTicket().orderNo)
        assertNull(testTicket(order = "").orderNo)
        assertEquals("CB-0001", testTicket(order = "CB-0001").orderNo)
    }

    @Test fun statusLabels() {
        assertEquals("Open", TicketStatus.label(TicketStatus.OPEN))
        assertEquals("Resolved", TicketStatus.label(TicketStatus.RESOLVED))
    }
}
