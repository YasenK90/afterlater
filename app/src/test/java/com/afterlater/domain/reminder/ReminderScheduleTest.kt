package com.afterlater.domain.reminder

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderScheduleTest {

    private val utc = ZoneOffset.UTC
    private val now = Instant.parse("2026-01-10T12:00:00Z")

    @Test
    fun `next trigger stays today when the chosen time is still ahead`() {
        assertEquals(
            Instant.parse("2026-01-10T18:30:00Z"),
            ReminderSchedule.nextTrigger(now, utc, hour = 18, minute = 30),
        )
    }

    @Test
    fun `next trigger moves to tomorrow when the chosen time has passed`() {
        assertEquals(
            Instant.parse("2026-01-11T08:00:00Z"),
            ReminderSchedule.nextTrigger(now, utc, hour = 8, minute = 0),
        )
    }

    @Test
    fun `next trigger does not fire twice at the exact chosen instant`() {
        assertEquals(
            Instant.parse("2026-01-11T12:00:00Z"),
            ReminderSchedule.nextTrigger(now, utc, hour = 12, minute = 0),
        )
    }

    @Test
    fun `next trigger follows the local wall clock across a daylight saving gap`() {
        val newYork = ZoneId.of("America/New_York")
        val beforeGap = Instant.parse("2026-03-07T12:00:00Z")

        assertEquals(
            Instant.parse("2026-03-08T07:30:00Z"),
            ReminderSchedule.nextTrigger(beforeGap, newYork, hour = 2, minute = 30),
        )
    }

    @Test
    fun `policy only allows one reminder for eligible work each local day`() {
        val today = LocalDate.parse("2026-01-10")

        assertTrue(
            ReminderPolicy.shouldNotify(
                enabled = true,
                eligibleCount = 2,
                today = today,
                lastNotifiedOn = null,
            ),
        )
        assertFalse(
            ReminderPolicy.shouldNotify(
                enabled = true,
                eligibleCount = 2,
                today = today,
                lastNotifiedOn = today,
            ),
        )
        assertFalse(
            ReminderPolicy.shouldNotify(
                enabled = true,
                eligibleCount = 0,
                today = today,
                lastNotifiedOn = null,
            ),
        )
        assertFalse(
            ReminderPolicy.shouldNotify(
                enabled = false,
                eligibleCount = 2,
                today = today,
                lastNotifiedOn = null,
            ),
        )
    }

    @Test
    fun `policy allows a new reminder the following local day`() {
        assertTrue(
            ReminderPolicy.shouldNotify(
                enabled = true,
                eligibleCount = 1,
                today = LocalDate.parse("2026-01-11"),
                lastNotifiedOn = LocalDate.parse("2026-01-10"),
            ),
        )
    }
}
