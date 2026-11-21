package com.crispyburger.admin.data

object TicketStatus {
    const val OPEN = "OPEN"
    const val RESOLVED = "RESOLVED"

    fun label(status: String): String = if (status == RESOLVED) "Resolved" else "Open"
}

enum class TicketFilter(val label: String) {
    OPEN("Open"),
    RESOLVED("Resolved")
}

object TicketRules {
    fun filter(tickets: List<AdminTicket>, filter: TicketFilter): List<AdminTicket> {
        val wanted = if (filter == TicketFilter.OPEN) TicketStatus.OPEN else TicketStatus.RESOLVED
        return tickets.filter { it.status == wanted }
    }

    fun openCount(tickets: List<AdminTicket>): Int = tickets.count { it.status == TicketStatus.OPEN }

    /** First non-empty line, shortened for list rows. */
    fun preview(message: String, max: Int = 90): String {
        val line = message.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()
        return if (line.length <= max) line else line.take(max - 1).trimEnd() + "…"
    }

    /** The status an admin action moves the ticket to. */
    fun toggled(status: String): String =
        if (status == TicketStatus.OPEN) TicketStatus.RESOLVED else TicketStatus.OPEN

    /** Digits-only phone for a tel: link, or null if it is not dialable. */
    fun dialable(phone: String): String? {
        val cleaned = phone.filter { it.isDigit() || it == '+' }
        return cleaned.takeIf { it.count { c -> c.isDigit() } in 9..13 }
    }
}
