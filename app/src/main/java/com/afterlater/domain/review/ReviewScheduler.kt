package com.afterlater.domain.review

import com.afterlater.core.model.ItemStatus
import com.afterlater.core.model.SavedItem
import java.time.Duration
import java.time.Instant

/**
 * Deterministic rediscovery rules. Nothing here touches Android or a database,
 * so the behaviour can be pinned down with fast unit tests.
 */
object ReviewScheduler {

    val FIRST_REVIEW_DELAY: Duration = Duration.ofHours(24)
    val REVIEW_INTERVAL: Duration = Duration.ofDays(7)
    val MAX_DAILY_ITEMS: Int = 3

    fun initialNextReviewAt(savedAt: Instant): Instant = savedAt.plus(FIRST_REVIEW_DELAY)

    fun nextAfterOpen(now: Instant): Instant = now.plus(REVIEW_INTERVAL)

    fun isEligible(item: SavedItem, now: Instant): Boolean {
        if (item.status != ItemStatus.NEW &&
            item.status != ItemStatus.READY &&
            item.status != ItemStatus.SNOOZED
        ) {
            return false
        }
        val due = item.nextReviewAt ?: return false
        if (due.isAfter(now)) return false
        val snoozedUntil = item.snoozedUntil ?: return true
        return !snoozedUntil.isAfter(now)
    }

    fun snoozeUntil(now: Instant, window: SnoozeWindow): Instant = now.plus(window.duration)

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

        appendGroup(eligible.filter { it.lastReviewedAt == null })
        appendGroup(eligible.filter { it.lastReviewedAt != null })
        return selected
    }
}

enum class SnoozeWindow(val duration: Duration) {
    TOMORROW(Duration.ofDays(1)),
    NEXT_WEEK(Duration.ofDays(7)),
}
