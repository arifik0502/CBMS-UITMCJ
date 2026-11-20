package com.crispyburger.admin.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventDedupeTest {
    @Test fun firstEventPosts() {
        val r = EventDedupe.check("", "order:new:1", nowMs = 1_000)
        assertTrue(r.shouldPost)
        assertEquals("order:new:1|1000", r.updatedState)
    }

    @Test fun sameEventInsideWindowIsSuppressed() {
        val first = EventDedupe.check("", "order:new:1", 1_000)
        val second = EventDedupe.check(first.updatedState, "order:new:1", 5_000)
        assertFalse(second.shouldPost)
        assertEquals(first.updatedState, second.updatedState)
    }

    @Test fun sameEventAfterWindowPostsAgain() {
        val first = EventDedupe.check("", "order:new:1", 1_000)
        val later = EventDedupe.check(first.updatedState, "order:new:1", 1_000 + EventDedupe.WINDOW_MS + 1)
        assertTrue(later.shouldPost)
    }

    @Test fun differentKeysDoNotCollide() {
        val first = EventDedupe.check("", "payment:1:VERIFIED", 1_000)
        assertTrue(EventDedupe.check(first.updatedState, "payment:1:REJECTED", 1_001).shouldPost)
    }

    @Test fun oldestEntriesAreEvictedInOrder() {
        var state = ""
        for (i in 1..5) state = EventDedupe.check(state, "k$i", i * 1_000L, maxEntries = 3).updatedState
        assertEquals(listOf("k3", "k4", "k5"), state.lines().map { it.substringBefore('|') })
    }

    @Test fun keysWithNewlinesAreSanitised() {
        val r = EventDedupe.check("", "bad\nkey", 1_000)
        assertEquals("bad key|1000", r.updatedState)
    }

    @Test fun corruptStateIsIgnored() {
        assertTrue(EventDedupe.check("garbage-without-separator", "k", 1_000).shouldPost)
    }
}
