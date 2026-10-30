package com.afterlater.domain.reminder

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object ReminderSchedule {

    fun nextTrigger(
        now: Instant,
        zone: ZoneId,
        hour: Int,
        minute: Int,
    ): Instant {
        val safeHour = hour.coerceIn(0, 23)
        val safeMinute = minute.coerceIn(0, 59)
        val today = now.atZone(zone)
            .toLocalDate()
            .atTime(safeHour, safeMinute)
            .atZone(zone)
            .toInstant()
        return if (today.isAfter(now)) today else today.atZone(zone).plusDays(1).toInstant()
    }

    fun localDate(now: Instant, zone: ZoneId): LocalDate = now.atZone(zone).toLocalDate()
}

object ReminderPolicy {

    fun shouldNotify(
        enabled: Boolean,
        eligibleCount: Int,
        today: LocalDate,
        lastNotifiedOn: LocalDate?,
    ): Boolean = enabled && eligibleCount > 0 && lastNotifiedOn != today
}
