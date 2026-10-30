package com.afterlater.data.export

import com.afterlater.core.model.ArchiveSnapshot
import com.afterlater.core.model.ItemStatus
import com.afterlater.core.model.MetadataStatus
import com.afterlater.core.model.ReviewAction
import com.afterlater.core.model.ReviewEvent
import com.afterlater.core.model.SavedItem
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveRestorePlannerTest {

    @Test
    fun `every item in a fully new backup is planned and its events keep their order`() {
        val plan = ArchiveRestorePlanner.plan(
            backup = ArchiveBackup(
                exportedAt = T1,
                items = listOf(item("a", "https://a.example/1"), item("b", "https://b.example/1")),
                events = listOf(
                    event(11, "a", ReviewAction.OPENED),
                    event(12, "b", ReviewAction.SNOOZED),
                ),
            ),
            existing = ArchiveSnapshot(emptyList(), emptyList()),
        )

        assertEquals(listOf("a", "b"), plan.newItems.map { it.id })
        assertEquals(0, plan.duplicateCount)
        assertEquals(0, plan.skippedEventCount)
        assertEquals(listOf("a", "b"), plan.newEvents.map { it.itemId })
    }

    @Test
    fun `planned events are reinserted with a fresh row id`() {
        val plan = ArchiveRestorePlanner.plan(
            backup = ArchiveBackup(
                exportedAt = T1,
                items = listOf(item("a", "https://a.example/1")),
                events = listOf(event(71, "a", ReviewAction.ARCHIVED)),
            ),
            existing = ArchiveSnapshot(emptyList(), emptyList()),
        )

        assertEquals(listOf(0L), plan.newEvents.map { it.id })
    }

    @Test
    fun `items matching an existing id or canonical url are counted as duplicates`() {
        val plan = ArchiveRestorePlanner.plan(
            backup = ArchiveBackup(
                exportedAt = T1,
                items = listOf(
                    item("known-id", "https://new.example/1"),
                    item("new-id", "https://known.example/1"),
                    item("fresh-id", "https://fresh.example/1"),
                ),
                events = emptyList(),
            ),
            existing = ArchiveSnapshot(
                items = listOf(
                    item("known-id", "https://known.example/other"),
                    item("other-id", "https://known.example/1"),
                ),
                events = emptyList(),
            ),
        )

        assertEquals(listOf("fresh-id"), plan.newItems.map { it.id })
        assertEquals(2, plan.duplicateCount)
        assertEquals(3, plan.backupItemCount)
    }

    @Test
    fun `a repeated id or url inside one backup is kept only once`() {
        val plan = ArchiveRestorePlanner.plan(
            backup = ArchiveBackup(
                exportedAt = T1,
                items = listOf(
                    item("same-id", "https://one.example/1"),
                    item("same-id", "https://two.example/1"),
                    item("other-id", "https://one.example/1"),
                    item("third-id", "https://three.example/1"),
                ),
                events = emptyList(),
            ),
            existing = ArchiveSnapshot(emptyList(), emptyList()),
        )

        assertEquals(listOf("same-id", "third-id"), plan.newItems.map { it.id })
        assertEquals(2, plan.duplicateCount)
    }

    @Test
    fun `events for duplicate or unknown items are skipped and counted`() {
        val plan = ArchiveRestorePlanner.plan(
            backup = ArchiveBackup(
                exportedAt = T1,
                items = listOf(
                    item("kept", "https://kept.example/1"),
                    item("dropped", "https://known.example/1"),
                ),
                events = listOf(
                    event(1, "kept", ReviewAction.OPENED),
                    event(2, "dropped", ReviewAction.OPENED),
                    event(3, "missing", ReviewAction.OPENED),
                ),
            ),
            existing = ArchiveSnapshot(
                items = listOf(item("dropped", "https://known.example/1")),
                events = emptyList(),
            ),
        )

        assertEquals(listOf("kept"), plan.newEvents.map { it.itemId })
        assertEquals(2, plan.skippedEventCount)
    }

    @Test
    fun `an empty backup plans nothing`() {
        val plan = ArchiveRestorePlanner.plan(
            backup = ArchiveBackup(T1, emptyList(), emptyList()),
            existing = ArchiveSnapshot(
                items = listOf(item("a", "https://a.example/1")),
                events = emptyList(),
            ),
        )

        assertTrue(plan.newItems.isEmpty())
        assertTrue(plan.newEvents.isEmpty())
        assertEquals(0, plan.duplicateCount)
        assertEquals(0, plan.newItemCount)
    }

    private companion object {
        val T1 = Instant.parse("2026-10-01T09:00:00Z")
    }

    private fun item(id: String, canonicalUrl: String): SavedItem = SavedItem(
        id = id,
        originalUrl = canonicalUrl,
        canonicalUrl = canonicalUrl,
        title = null,
        description = null,
        domain = "example.com",
        thumbnailUrl = null,
        savedAt = T1,
        updatedAt = T1,
        status = ItemStatus.READY,
        lastReviewedAt = null,
        nextReviewAt = T1,
        snoozedUntil = null,
        personalNote = null,
        metadataStatus = MetadataStatus.SUCCESS,
    )

    private fun event(id: Long, itemId: String, action: ReviewAction): ReviewEvent = ReviewEvent(
        id = id,
        itemId = itemId,
        action = action,
        timestamp = T1,
        snoozeUntil = null,
    )
}
