package com.crispyburger.admin.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyAndFormatsTest {
    @Test fun formatsCents() {
        assertEquals("RM 0.00", Money.format(0))
        assertEquals("RM 6.50", Money.format(650))
        assertEquals("RM 12.05", Money.format(1205L))
    }

    @Test fun negativeClampsToZero() {
        assertEquals("RM 0.00", Money.format(-5))
    }

    @Test fun plainHasNoPrefix() {
        assertEquals("6.50", Money.plain(650))
    }

    @Test fun utcTimestampShownInMalaysiaTime() {
        // 16:30 UTC on the 18th is 00:30 on the 19th in Malaysia (UTC+8).
        assertEquals(LocalDate.of(2026, 11, 19), Formats.malaysiaLocalDate("2026-11-18T16:30:00+00:00"))
        assertEquals("19 Nov 2026, 12:30 AM", Formats.malaysiaDateTime("2026-11-18T16:30:00+00:00"))
    }

    @Test fun zSuffixAlsoParses() {
        assertEquals(LocalDate.of(2026, 11, 19), Formats.malaysiaLocalDate("2026-11-18T16:30:00Z"))
    }

    @Test fun garbageIsReturnedAsIs() {
        assertEquals("not a date", Formats.malaysiaDateTime("not a date"))
        assertNull(Formats.malaysiaLocalDate(null))
    }

    @Test fun tripTimeFormats() {
        assertEquals("8:15 AM", Formats.tripTime("08:15:00"))
        assertEquals("12:00 PM", Formats.tripTime("12:00:00"))
        assertEquals("12:30 AM", Formats.tripTime("00:30:00"))
        assertEquals("6:45 PM", Formats.tripTime("18:45:00"))
    }
}
