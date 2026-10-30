package com.afterlater.core.util

import com.afterlater.domain.review.Deadline
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class TimeFormatTest {

    private val zone = ZoneId.of("+02:00")
    private val now = Instant.parse("2026-03-10T09:00:00Z")

    @Test
    fun `a deadline in the past is expired`() {
        assertEquals("EXPIRED", TimeFormat.expiryLabel(now.minusSeconds(1), now, zone))
    }

    @Test
    fun `the deadline instant itself counts as expired`() {
        assertEquals("EXPIRED", TimeFormat.expiryLabel(now, now, zone))
    }

    @Test
    fun `end of today reads as tonight`() {
        val deadline = Deadline.endOfToday(now, zone)

        assertEquals("ENDS TONIGHT", TimeFormat.expiryLabel(deadline, now, zone))
    }

    @Test
    fun `end of tomorrow reads as tomorrow`() {
        val deadline = Deadline.endOfToday(now, zone).plusSeconds(24 * 60 * 60)

        assertEquals("ENDS TOMORROW", TimeFormat.expiryLabel(deadline, now, zone))
    }

    @Test
    fun `a week out counts days`() {
        val deadline = Deadline.endOfWeek(now, zone)

        assertEquals("ENDS IN 7D", TimeFormat.expiryLabel(deadline, now, zone))
    }

    @Test
    fun `a deadline seconds away still counts as tonight`() {
        assertEquals("ENDS TONIGHT", TimeFormat.expiryLabel(now.plusSeconds(30), now, zone))
    }

    @Test
    fun `the label counts local days rather than elapsed hours`() {
        val deadline = Instant.parse("2026-03-11T21:59:00Z")

        assertEquals("ENDS TOMORROW", TimeFormat.expiryLabel(deadline, now, ZoneId.of("+02:00")))
        assertEquals("ENDS IN 2D", TimeFormat.expiryLabel(deadline, now, ZoneId.of("+05:00")))
    }

    @Test
    fun `relative past never reads negative`() {
        assertEquals("JUST NOW", TimeFormat.relativePast(now.plusSeconds(60), now))
    }
}
