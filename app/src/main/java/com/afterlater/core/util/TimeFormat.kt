package com.afterlater.core.util

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object TimeFormat {

    private val date = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.US)
    private val dateTime = DateTimeFormatter.ofPattern("dd MMM yyyy · HH:mm", Locale.US)
    private val shortDate = DateTimeFormatter.ofPattern("dd.MM.yy", Locale.US)
    private val clock = DateTimeFormatter.ofPattern("HH:mm", Locale.US)

    fun date(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
        date.format(instant.atZone(zone)).uppercase(Locale.US)

    fun dateTime(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
        dateTime.format(instant.atZone(zone)).uppercase(Locale.US)

    fun shortDate(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
        shortDate.format(instant.atZone(zone))

    fun clock(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
        clock.format(instant.atZone(zone))

    fun relativePast(instant: Instant, now: Instant): String {
        val duration = Duration.between(instant, now)
        if (duration.isNegative) return "JUST NOW"
        val minutes = duration.toMinutes()
        val hours = duration.toHours()
        val days = duration.toDays()
        return when {
            minutes < 1 -> "JUST NOW"
            hours < 1 -> "${minutes}M AGO"
            days < 1 -> "${hours}H AGO"
            days < 30 -> "${days}D AGO"
            days < 365 -> "${days / 30}MO AGO"
            else -> "${days / 365}Y AGO"
        }
    }

    fun relativeFuture(instant: Instant, now: Instant): String {
        val duration = Duration.between(now, instant)
        if (duration.isNegative || duration.isZero) return "NOW"
        val minutes = duration.toMinutes()
        val hours = duration.toHours()
        val days = duration.toDays()
        return when {
            hours < 1 -> "${minutes.coerceAtLeast(1)}M"
            days < 1 -> "${hours}H"
            days < 30 -> "${days}D"
            else -> "${days / 30}MO"
        }
    }

    fun dueLabel(nextReviewAt: Instant?, now: Instant): String = when {
        nextReviewAt == null -> "NOT IN ROTATION"
        nextReviewAt.isAfter(now) -> "NEXT IN ${relativeFuture(nextReviewAt, now)}"
        else -> "DUE ${relativePast(nextReviewAt, now)}"
    }

    fun countLabel(count: Int, singular: String, plural: String): String =
        "$count ${if (count == 1) singular else plural}".uppercase(Locale.US)
}
