package com.afterlater.domain.review

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class DeadlineTest {

    private val now = Instant.parse("2026-03-10T09:00:00Z")

    @Test
    fun `end of today is the next local midnight`() {
        assertEquals(
            Instant.parse("2026-03-11T00:00:00Z"),
            Deadline.endOfToday(now, ZoneOffset.UTC),
        )
    }

    @Test
    fun `end of today follows the local zone`() {
        assertEquals(
            Instant.parse("2026-03-10T18:30:00Z"),
            Deadline.endOfToday(now, ZoneId.of("+05:30")),
        )
    }

    @Test
    fun `end of week is seven days of local days`() {
        assertEquals(
            Instant.parse("2026-03-18T00:00:00Z"),
            Deadline.endOfWeek(now, ZoneOffset.UTC),
        )
    }

    @Test
    fun `a deadline set today is still in the future`() {
        val endOfToday = Deadline.endOfToday(now, ZoneOffset.UTC)

        assertEquals(true, endOfToday.isAfter(now))
        assertEquals(true, Deadline.endOfWeek(now, ZoneOffset.UTC).isAfter(endOfToday))
    }
}
