package com.afterlater.domain.review

import com.afterlater.core.model.ItemStatus
import com.afterlater.core.model.MetadataStatus
import com.afterlater.core.model.SavedItem
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class SmartSorterTest {

    private val now = Instant.parse("2026-10-31T12:00:00Z")

    @Test
    fun `live deadlines lead in urgency order`() {
        val ranked = SmartSorter.rank(
            listOf(
                item("later-deadline", expiresAt = now.plusSeconds(3_600)),
                item("never-opened", lastReviewedAt = null),
                item("soonest-deadline", expiresAt = now.plusSeconds(600)),
            ),
        )

        assertEquals(listOf("soonest-deadline", "later-deadline", "never-opened"), ranked.map { it.id })
    }

    @Test
    fun `never-opened entries outrank reviewed ones`() {
        val ranked = SmartSorter.rank(
            listOf(
                item("opened-recently", lastReviewedAt = now.minusSeconds(60)),
                item("never-opened", lastReviewedAt = null, savedAt = now.minusSeconds(120)),
                item("opened-long-ago", lastReviewedAt = now.minusSeconds(86_400)),
            ),
        )

        assertEquals(listOf("never-opened", "opened-long-ago", "opened-recently"), ranked.map { it.id })
    }

    @Test
    fun `reviewed entries resurface by how long they were ignored`() {
        val ranked = SmartSorter.rank(
            listOf(
                item("old-save-reviewed-yesterday", savedAt = now.minusSeconds(86_400 * 30), lastReviewedAt = now.minusSeconds(3_600)),
                item("new-save-reviewed-weeks-ago", savedAt = now.minusSeconds(3_600), lastReviewedAt = now.minusSeconds(86_400 * 7)),
            ),
        )

        // The entry untouched for seven days leads, despite being a newer save.
        assertEquals(listOf("new-save-reviewed-weeks-ago", "old-save-reviewed-yesterday"), ranked.map { it.id })
    }

    @Test
    fun `never-opened entries resurface oldest save first`() {
        val ranked = SmartSorter.rank(
            listOf(
                item("newer", lastReviewedAt = null, savedAt = now.minusSeconds(500)),
                item("older", lastReviewedAt = null, savedAt = now.minusSeconds(5_000)),
            ),
        )

        assertEquals(listOf("older", "newer"), ranked.map { it.id })
    }

    @Test
    fun `equal keys break the tie by id`() {
        val ranked = SmartSorter.rank(
            listOf(item("b"), item("a")),
        )

        assertEquals(listOf("a", "b"), ranked.map { it.id })
    }

    @Test
    fun `deadline entries with identical deadlines fall through to the other tiers`() {
        val sharedDeadline = now.plusSeconds(1_800)
        val ranked = SmartSorter.rank(
            listOf(
                item("deadline-opened", expiresAt = sharedDeadline, lastReviewedAt = now.minusSeconds(60)),
                item("deadline-never-opened", expiresAt = sharedDeadline, lastReviewedAt = null),
            ),
        )

        assertEquals(listOf("deadline-never-opened", "deadline-opened"), ranked.map { it.id })
    }

    private fun item(
        id: String,
        savedAt: Instant = now.minusSeconds(1_000),
        lastReviewedAt: Instant? = now,
        expiresAt: Instant? = null,
    ) = SavedItem(
        id = id,
        originalUrl = "https://example.com/$id",
        canonicalUrl = "https://example.com/$id",
        title = id,
        description = null,
        domain = "example.com",
        thumbnailUrl = null,
        savedAt = savedAt,
        updatedAt = savedAt,
        status = ItemStatus.READY,
        lastReviewedAt = lastReviewedAt,
        nextReviewAt = now,
        snoozedUntil = null,
        personalNote = null,
        metadataStatus = MetadataStatus.PENDING,
        expiresAt = expiresAt,
    )
}
