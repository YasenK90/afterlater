package com.afterlater.domain.review

import java.time.Instant
import java.time.ZoneId

enum class ExpiryChoice {
    NONE,
    TODAY,
    THIS_WEEK,
}

fun ExpiryChoice.resolve(now: Instant, zone: ZoneId): Instant? = when (this) {
    ExpiryChoice.NONE -> null
    ExpiryChoice.TODAY -> Deadline.endOfToday(now, zone)
    ExpiryChoice.THIS_WEEK -> Deadline.endOfWeek(now, zone)
}
