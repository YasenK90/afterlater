package com.afterlater.test

import com.afterlater.core.model.ItemStatus
import com.afterlater.core.model.MetadataStatus
import com.afterlater.core.model.SavedItem
import java.time.Instant

fun savedItem(
    id: String = "item-1",
    status: ItemStatus = ItemStatus.NEW,
    title: String? = "A saved link",
    personalNote: String? = null,
): SavedItem {
    val savedAt = Instant.parse("2026-01-01T00:00:00Z")
    return SavedItem(
        id = id,
        originalUrl = "https://example.com/article",
        canonicalUrl = "https://example.com/article",
        title = title,
        description = "A useful article.",
        domain = "example.com",
        thumbnailUrl = null,
        savedAt = savedAt,
        updatedAt = savedAt,
        status = status,
        lastReviewedAt = null,
        nextReviewAt = if (status == ItemStatus.NEW) savedAt else null,
        snoozedUntil = null,
        personalNote = personalNote,
        metadataStatus = MetadataStatus.SUCCESS,
    )
}
