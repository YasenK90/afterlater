package com.afterlater.domain.review

import com.afterlater.core.model.AppSettings
import com.afterlater.core.model.ItemStatus
import com.afterlater.core.model.MetadataStatus
import com.afterlater.core.model.ReviewUndo
import com.afterlater.core.model.SavedItem
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewSchedulerTest {

    private val now = Instant.parse("2026-01-10T12:00:00Z")

    @Test
    fun `first review is one day after saving and opens move it a week out`() {
        val savedAt = Instant.parse("2026-01-01T08:30:00Z")

        assertEquals(
            Instant.parse("2026-01-02T08:30:00Z"),
            ReviewScheduler.initialNextReviewAt(savedAt),
        )
        assertEquals(
            Instant.parse("2026-01-17T12:00:00Z"),
            ReviewScheduler.nextAfterOpen(now),
        )
    }

    @Test
    fun `eligibility requires a due reviewable item that is not snoozed`() {
        assertTrue(ReviewScheduler.isEligible(item(due = now.minusSeconds(60)), now))
        assertTrue(ReviewScheduler.isEligible(item(due = now), now))

        assertFalse(ReviewScheduler.isEligible(item(due = now.plusSeconds(60)), now))
        assertFalse(ReviewScheduler.isEligible(item(due = null), now))
        assertFalse(
            ReviewScheduler.isEligible(
                item(due = now.minusSeconds(60), snoozedUntil = now.plusSeconds(60)),
                now,
            ),
        )
        assertTrue(
            ReviewScheduler.isEligible(
                item(due = now.minusSeconds(60), snoozedUntil = now),
                now,
            ),
        )
        assertTrue(
            ReviewScheduler.isEligible(
                item(due = now.minusSeconds(60), status = ItemStatus.USEFUL),
                now,
            ),
        )
        assertFalse(
            ReviewScheduler.isEligible(
                item(due = null, status = ItemStatus.USEFUL),
                now,
            ),
        )
        assertFalse(
            ReviewScheduler.isEligible(
                item(due = now.plusSeconds(60), status = ItemStatus.USEFUL),
                now,
            ),
        )
        assertFalse(
            ReviewScheduler.isEligible(
                item(due = now.minusSeconds(60), status = ItemStatus.ARCHIVED),
                now,
            ),
        )
        assertTrue(
            ReviewScheduler.isEligible(
                item(
                    due = now.minusSeconds(60),
                    status = ItemStatus.SNOOZED,
                    snoozedUntil = now.minusSeconds(1),
                ),
                now,
            ),
        )
        assertFalse(
            ReviewScheduler.isEligible(
                item(
                    due = now.minusSeconds(60),
                    status = ItemStatus.SNOOZED,
                    snoozedUntil = now.plusSeconds(1),
                ),
                now,
            ),
        )
    }

    @Test
    fun `snooze windows are exactly tomorrow and next week`() {
        assertEquals(
            now.plus(Duration.ofDays(1)),
            ReviewScheduler.snoozeUntil(now, SnoozeWindow.TOMORROW),
        )
        assertEquals(
            now.plus(Duration.ofDays(7)),
            ReviewScheduler.snoozeUntil(now, SnoozeWindow.NEXT_WEEK),
        )
    }

    @Test
    fun `daily batch prioritizes never reviewed entries then older saves`() {
        val reviewedOldest = item(
            id = "reviewed-oldest",
            domain = "alpha.com",
            due = now.minusSeconds(300),
            savedAt = now.minus(Duration.ofDays(30)),
            lastReviewedAt = now.minus(Duration.ofDays(2)),
        )
        val neverOlder = item(
            id = "never-older",
            domain = "beta.com",
            due = now.minusSeconds(100),
            savedAt = now.minus(Duration.ofDays(10)),
        )
        val neverNewer = item(
            id = "never-newer",
            domain = "gamma.com",
            due = now.minusSeconds(200),
            savedAt = now.minus(Duration.ofDays(2)),
        )
        val reviewedNewest = item(
            id = "reviewed-newest",
            domain = "delta.com",
            due = now.minusSeconds(400),
            savedAt = now.minus(Duration.ofDays(1)),
            lastReviewedAt = now.minus(Duration.ofDays(1)),
        )

        val batch = ReviewScheduler.selectDailyBatch(
            listOf(reviewedNewest, neverNewer, reviewedOldest, neverOlder),
            limit = 3,
            now = now,
        )

        assertEquals(listOf("never-older", "never-newer", "reviewed-oldest"), batch.map { it.id })
    }

    @Test
    fun `never reviewed entries fill before reviewed entries even within one domain`() {
        val neverFirst = item(
            id = "never-first",
            domain = "alpha.com",
            due = now.minusSeconds(60),
            savedAt = now.minus(Duration.ofDays(5)),
        )
        val neverSecond = item(
            id = "never-second",
            domain = "alpha.com",
            due = now.minusSeconds(50),
            savedAt = now.minus(Duration.ofDays(4)),
        )
        val reviewed = item(
            id = "reviewed",
            domain = "beta.com",
            due = now.minusSeconds(40),
            savedAt = now.minus(Duration.ofDays(20)),
            lastReviewedAt = now.minus(Duration.ofDays(1)),
        )

        val batch = ReviewScheduler.selectDailyBatch(
            listOf(reviewed, neverSecond, neverFirst),
            limit = 2,
            now = now,
        )

        assertEquals(listOf("never-first", "never-second"), batch.map { it.id })
    }

    @Test
    fun `daily batch is capped at three even when callers ask for more`() {
        val candidates = (1..5).map { index ->
            item(
                id = "item-$index",
                domain = "site$index.com",
                due = now.minusSeconds(index.toLong()),
                savedAt = now.minus(Duration.ofDays(index.toLong())),
            )
        }

        assertEquals(3, ReviewScheduler.MAX_DAILY_ITEMS)
        assertEquals(3, AppSettings.MAX_DAILY_LIMIT)
        assertEquals(3, ReviewScheduler.selectDailyBatch(candidates, limit = 5, now = now).size)
    }

    @Test
    fun `undo is only applicable while the action result is still current`() {
        val expected = item(
            id = "changed",
            due = now.plus(Duration.ofDays(7)),
            status = ItemStatus.READY,
            lastReviewedAt = now,
        )
        val undo = ReviewUndo(
            itemId = expected.id,
            previous = expected.reviewSnapshot().copy(
                status = ItemStatus.SNOOZED,
                lastReviewedAt = null,
                nextReviewAt = now.minusSeconds(60),
                snoozedUntil = now.minusSeconds(60),
            ),
            expected = expected.reviewSnapshot(),
        )

        assertTrue(undo.isApplicableTo(expected))
        assertFalse(undo.isApplicableTo(expected.copy(status = ItemStatus.USEFUL)))
        assertFalse(undo.isApplicableTo(expected.copy(lastReviewedAt = now.plusSeconds(1))))
    }

    @Test
    fun `daily batch prefers domain diversity before repeating a domain`() {
        val alphaOld = item(id = "a1", domain = "alpha.com", due = now.minusSeconds(10_800))
        val alphaNew = item(id = "a2", domain = "alpha.com", due = now.minusSeconds(7_200))
        val beta = item(id = "b1", domain = "beta.com", due = now.minusSeconds(3_600))

        val batch = ReviewScheduler.selectDailyBatch(listOf(alphaOld, alphaNew, beta), limit = 2, now = now)

        assertEquals(listOf("a1", "b1"), batch.map { it.id })
    }

    @Test
    fun `daily batch never exceeds the limit and appends deferred items`() {
        val alphaOld = item(id = "a1", domain = "alpha.com", due = now.minusSeconds(10_800))
        val alphaNew = item(id = "a2", domain = "alpha.com", due = now.minusSeconds(7_200))
        val beta = item(id = "b1", domain = "beta.com", due = now.minusSeconds(3_600))

        assertEquals(1, ReviewScheduler.selectDailyBatch(listOf(alphaOld, alphaNew, beta), 1, now).size)
        assertEquals(2, ReviewScheduler.selectDailyBatch(listOf(alphaOld, alphaNew, beta), 2, now).size)

        val all = ReviewScheduler.selectDailyBatch(listOf(alphaOld, alphaNew, beta), 10, now)
        assertEquals(listOf("a1", "b1", "a2"), all.map { it.id })

        assertTrue(ReviewScheduler.selectDailyBatch(listOf(alphaOld), 0, now).isEmpty())
        assertTrue(ReviewScheduler.selectDailyBatch(listOf(alphaOld), -1, now).isEmpty())
    }

    @Test
    fun `snoozed item returns once its pause expires`() {
        val expired = item(
            id = "expired",
            due = now.minusSeconds(60),
            status = ItemStatus.SNOOZED,
            snoozedUntil = now.minusSeconds(1),
        )
        val paused = item(
            id = "paused",
            due = now.minusSeconds(60),
            status = ItemStatus.SNOOZED,
            snoozedUntil = now.plusSeconds(60),
        )

        assertEquals(
            listOf("expired"),
            ReviewScheduler.selectDailyBatch(listOf(paused, expired), 5, now).map { it.id },
        )
    }

    @Test
    fun `daily batch ignores future and archived items`() {
        val future = item(id = "future", due = now.plusSeconds(60))
        val archived = item(id = "archived", due = now.minusSeconds(60), status = ItemStatus.ARCHIVED)
        val ready = item(id = "ready", due = now.minusSeconds(60))

        val batch = ReviewScheduler.selectDailyBatch(listOf(future, archived, ready), 5, now)

        assertEquals(listOf("ready"), batch.map { it.id })
    }

    @Test
    fun `daily batch ordering is stable for identical timestamps`() {
        val savedAt = now.minus(Duration.ofDays(2))
        val first = item(id = "a", domain = "alpha.com", due = now.minusSeconds(60), savedAt = savedAt)
        val second = item(id = "b", domain = "beta.com", due = now.minusSeconds(60), savedAt = savedAt)

        val batch = ReviewScheduler.selectDailyBatch(listOf(second, first), 2, now)

        assertEquals(listOf("a", "b"), batch.map { it.id })
    }

    @Test
    fun `a live deadline surfaces before the settle delay`() {
        val justSaved = now.minusSeconds(30)

        assertTrue(
            ReviewScheduler.isEligible(
                item(due = now.plus(Duration.ofHours(23)), savedAt = justSaved, expiresAt = now.plus(Duration.ofHours(2))),
                now,
            ),
        )
    }

    @Test
    fun `an expired deadline is never eligible again`() {
        assertFalse(
            ReviewScheduler.isEligible(
                item(due = now.minusSeconds(60), expiresAt = now),
                now,
            ),
        )
        assertFalse(
            ReviewScheduler.isEligible(
                item(due = now.minusSeconds(60), expiresAt = now.minusSeconds(1)),
                now,
            ),
        )
    }

    @Test
    fun `a deadline stops forcing eligibility once the item is reviewed`() {
        val reviewed = item(
            due = now.plus(Duration.ofDays(7)),
            savedAt = now.minus(Duration.ofDays(2)),
            lastReviewedAt = now.minusSeconds(10),
            expiresAt = now.plus(Duration.ofHours(2)),
        )

        assertFalse(ReviewScheduler.isEligible(reviewed, now))
    }

    @Test
    fun `daily batch leads with the soonest deadline`() {
        val ordinary = item(id = "ordinary", domain = "alpha.com", due = now.minusSeconds(400))
        val laterDeadline = item(
            id = "later",
            domain = "beta.com",
            due = now.minusSeconds(300),
            expiresAt = now.plus(Duration.ofHours(9)),
        )
        val soonerDeadline = item(
            id = "sooner",
            domain = "gamma.com",
            due = now.minusSeconds(200),
            expiresAt = now.plus(Duration.ofHours(2)),
        )

        val batch = ReviewScheduler.selectDailyBatch(
            listOf(ordinary, laterDeadline, soonerDeadline),
            limit = 3,
            now = now,
        )

        assertEquals(listOf("sooner", "later", "ordinary"), batch.map { it.id })
    }

    @Test
    fun `snooze cannot outlive a deadline`() {
        val deadline = now.plus(Duration.ofHours(5))

        assertEquals(
            deadline,
            ReviewScheduler.snoozeUntil(now, SnoozeWindow.NEXT_WEEK, deadline),
        )
        assertEquals(
            now.plus(Duration.ofDays(1)),
            ReviewScheduler.snoozeUntil(now, SnoozeWindow.TOMORROW, deadline.plus(Duration.ofDays(30))),
        )
    }

    private fun item(
        id: String = "item",
        domain: String = "example.com",
        due: Instant?,
        savedAt: Instant = now.minus(Duration.ofDays(3)),
        status: ItemStatus = ItemStatus.READY,
        lastReviewedAt: Instant? = null,
        snoozedUntil: Instant? = null,
        expiresAt: Instant? = null,
    ) = SavedItem(
        id = id,
        originalUrl = "https://$domain/$id",
        canonicalUrl = "https://$domain/$id",
        title = null,
        description = null,
        domain = domain,
        thumbnailUrl = null,
        savedAt = savedAt,
        updatedAt = savedAt,
        status = status,
        lastReviewedAt = lastReviewedAt,
        nextReviewAt = due,
        snoozedUntil = snoozedUntil,
        personalNote = null,
        metadataStatus = MetadataStatus.PENDING,
        expiresAt = expiresAt,
    )
}
