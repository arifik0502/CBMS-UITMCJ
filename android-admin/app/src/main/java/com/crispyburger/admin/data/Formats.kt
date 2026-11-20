package com.crispyburger.admin.data

import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** All user-facing dates are shown in Malaysia time (not UTC). */
object Formats {
    private val dateTime = DateTimeFormatter.ofPattern("dd MMM yyyy, h:mm a", Locale.ENGLISH)
    private val dateOnly = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)

    private fun parse(value: String?): ZonedDateTime? {
        if (value.isNullOrBlank()) return null
        return try {
            OffsetDateTime.parse(value).atZoneSameInstant(ReportRange.zone)
        } catch (_: Exception) {
            try {
                Instant.parse(value).atZone(ReportRange.zone)
            } catch (_: Exception) {
                null
            }
        }
    }

    fun malaysiaDateTime(value: String?): String =
        parse(value)?.format(dateTime) ?: value.orEmpty()

    fun malaysiaDate(value: String?): String =
        parse(value)?.format(dateOnly) ?: value.orEmpty()

    fun malaysiaLocalDate(value: String?): LocalDate? = parse(value)?.toLocalDate()

    /** "08:15:00" -> "8:15 AM" */
    fun tripTime(value: String): String {
        val parts = value.take(5).split(":")
        if (parts.size != 2) return value
        val hour = parts[0].toIntOrNull() ?: return value
        val minute = parts[1]
        val displayHour = when (hour % 12) { 0 -> 12; else -> hour % 12 }
        return "$displayHour:$minute ${if (hour < 12) "AM" else "PM"}"
    }
}
