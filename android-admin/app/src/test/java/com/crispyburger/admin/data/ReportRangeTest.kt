package com.crispyburger.admin.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ReportRangeTest {
    private val d = LocalDate.of(2026, 11, 19)

    @Test fun validRanges() {
        assertNull(ReportRange.validate(d, d))
        assertNull(ReportRange.validate(d.minusDays(366), d))
    }

    @Test fun rejectsReversedAndOversizedRanges() {
        assertNotNull(ReportRange.validate(d, d.minusDays(1)))
        assertNotNull(ReportRange.validate(d.minusDays(367), d))
    }

    @Test fun boundsAreMalaysiaMidnights() {
        val (from, to) = ReportRange.bounds(d, d)
        assertEquals("2026-11-18T16:00:00Z", from)
        assertEquals("2026-11-19T16:00:00Z", to)
    }

    @Test fun presets() {
        assertEquals(d.minusDays(6) to d, ReportRange.last7Days(d))
        assertEquals(LocalDate.of(2026, 11, 1) to d, ReportRange.thisMonth(d))
    }
}
