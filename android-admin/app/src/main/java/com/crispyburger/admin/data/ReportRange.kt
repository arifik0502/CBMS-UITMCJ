package com.crispyburger.admin.data

import java.time.LocalDate
import java.time.ZoneId

object ReportRange {
    const val MAX_SPAN_DAYS = 366L

    val zone: ZoneId = ZoneId.of("Asia/Kuala_Lumpur")

    fun today(): LocalDate = LocalDate.now(zone)

    /** Returns an error message, or null when the range is valid. */
    fun validate(from: LocalDate, to: LocalDate): String? = when {
        to.isBefore(from) -> "End date must be on or after start date."
        to.toEpochDay() - from.toEpochDay() > MAX_SPAN_DAYS -> "Report range cannot exceed 367 calendar days."
        else -> null
    }

    /** [from start of day, to start of next day) as ISO instants in Malaysia time. */
    fun bounds(from: LocalDate, to: LocalDate): Pair<String, String> =
        from.atStartOfDay(zone).toInstant().toString() to
            to.plusDays(1).atStartOfDay(zone).toInstant().toString()

    fun last7Days(today: LocalDate = today()): Pair<LocalDate, LocalDate> = today.minusDays(6) to today

    fun thisMonth(today: LocalDate = today()): Pair<LocalDate, LocalDate> = today.withDayOfMonth(1) to today
}
