package com.afterlater.domain.review

import java.time.Instant
import java.time.ZoneId

object Deadline {

    fun endOfToday(now: Instant, zone: ZoneId): Instant =
        now.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()

    fun endOfWeek(now: Instant, zone: ZoneId): Instant =
        now.atZone(zone).toLocalDate().plusDays(8).atStartOfDay(zone).toInstant()
}
