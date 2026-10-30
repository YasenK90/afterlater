package com.afterlater.domain.review

import com.afterlater.core.model.ItemStatus
import com.afterlater.core.model.SavedItem
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Deterministic rediscovery rules. Nothing here touches Android or a database,
 * so the behaviour can be pinned down with fast unit tests.
 */
object ReviewScheduler {

    val FIRST_REVIEW_DELAY: Duration = Duration.ofHours(24)
    val REVIEW_INTERVAL: Duration = Duration.ofDays(7)
    val MAX_DAILY_ITEMS: Int = 5

    fun initialNextReviewAt(savedAt: Instant): Instant = savedAt.plus(FIRST_REVIEW_DELAY)

    fun nextAfterOpen(now: Instant): Instant = now.plus(REVIEW_INTERVAL)

    fun isEligible(item: SavedItem, now: Instant): Boolean {
        if (item.status != ItemStatus.NEW &&
            item.status != ItemStatus.READY &&
            item.status != ItemStatus.SNOOZED &&
            item.status != ItemStatus.USEFUL
        ) {
            return false
        }
        val expiresAt = item.expiresAt
        if (expiresAt != null) {
            if (!expiresAt.isAfter(now)) return false
            if (!item.reviewedSinceSaving()) return true
        }
        val due = item.nextReviewAt ?: return false
        if (due.isAfter(now)) return false
        val snoozedUntil = item.snoozedUntil ?: return true
        return !snoozedUntil.isAfter(now)
    }

    fun snoozeUntil(now: Instant, window: SnoozeWindow, expiresAt: Instant? = null): Instant {
        val requested = now.plus(window.duration)
        return if (expiresAt != null && expiresAt.isBefore(requested)) expiresAt else requested
    }

    /**
     * Snoozing to a picked date returns the entry at the start of that day
     * (local time). A date that is not in the future falls back to tomorrow,
     * and a live deadline clamps the result exactly like [snoozeUntil].
     */
    fun snoozeUntilDate(
        now: Instant,
        zone: ZoneId,
        targetDate: LocalDate,
        expiresAt: Instant? = null,
    ): Instant {
        val requested = targetDate.atStartOfDay(zone).toInstant()
        val effective = if (requested.isAfter(now)) requested else now.plus(Duration.ofDays(1))
        return if (expiresAt != null && expiresAt.isBefore(effective)) expiresAt else effective
    }

    private fun SavedItem.reviewedSinceSaving(): Boolean =
        lastReviewedAt?.let { !it.isBefore(savedAt) } == true

    /**
     * Builds one day's batch. All never-reviewed entries are considered before
     * reviewed ones; older saves lead within each group. Domain diversity applies
     * inside a group, with a stable final ordering for deterministic UI.
     */
    fun selectDailyBatch(candidates: List<SavedItem>, limit: Int, now: Instant): List<SavedItem> {
        val boundedLimit = limit.coerceAtMost(MAX_DAILY_ITEMS)
        if (boundedLimit <= 0) return emptyList()
        val eligible = candidates
            .filter { isEligible(it, now) }
            .sortedWith(
                compareBy(
                    { it.savedAt },
                    { it.nextReviewAt ?: Instant.MAX },
                    { it.id },
                ),
            )

        val selected = ArrayList<SavedItem>(boundedLimit)
        val seenDomains = HashSet<String>(boundedLimit)

        fun appendGroup(group: List<SavedItem>) {
            val deferred = ArrayList<SavedItem>()
            for (item in group) {
                if (selected.size >= boundedLimit) return
                if (item.domainKey !in seenDomains) {
                    selected += item
                    seenDomains += item.domainKey
                } else {
                    deferred += item
                }
            }
            for (item in deferred) {
                if (selected.size >= boundedLimit) break
                selected += item
            }
        }

        appendGroup(eligible.filter { it.expiresAt != null }.sortedBy { it.expiresAt })
        appendGroup(eligible.filter { it.expiresAt == null && it.lastReviewedAt == null })
        appendGroup(eligible.filter { it.expiresAt == null && it.lastReviewedAt != null })
        return selected
    }
}

/**
 * A snooze span. [TOMORROW] and [NEXT_WEEK] are the named presets; a date the
 * user picks in the snooze dialog becomes a plain duration-based window built
 * from [ReviewScheduler.snoozeUntilDate].
 */
data class SnoozeWindow(val duration: Duration) {
    companion object {
        val TOMORROW = SnoozeWindow(Duration.ofDays(1))
        val NEXT_WEEK = SnoozeWindow(Duration.ofDays(7))
    }
}
