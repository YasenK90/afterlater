package com.afterlater.core.model

import java.time.Instant

/**
 * A link the user deliberately saved for later.
 *
 * Timestamps are [Instant]s so they survive timezone changes unchanged. They are
 * rendered in the device's current zone at display time.
 */
data class SavedItem(
    val id: String,
    val originalUrl: String,
    val canonicalUrl: String,
    val title: String?,
    val description: String?,
    val domain: String,
    val thumbnailUrl: String?,
    val savedAt: Instant,
    val updatedAt: Instant,
    val status: ItemStatus,
    val lastReviewedAt: Instant?,
    val nextReviewAt: Instant?,
    val snoozedUntil: Instant?,
    val personalNote: String?,
    val metadataStatus: MetadataStatus,
    val surfaceHint: SurfaceHint = SurfaceHint.QUEUE,
    val expiresAt: Instant? = null,
) {
    /** Stable key used to avoid showing two links from one site in a batch. */
    val domainKey: String
        get() = domain.lowercase().removePrefix("www.")

    /** A title that always has something readable to show. */
    val displayTitle: String
        get() = title?.trim()?.takeIf { it.isNotEmpty() } ?: domain

    val isReviewable: Boolean
        get() = status == ItemStatus.NEW ||
            status == ItemStatus.READY ||
            status == ItemStatus.SNOOZED

    fun isExpired(now: Instant): Boolean = expiresAt?.let { !it.isAfter(now) } ?: false

    /** The review fields that an action may change, used for a precise undo. */
    fun reviewSnapshot(): ReviewSnapshot = ReviewSnapshot(
        status = status,
        lastReviewedAt = lastReviewedAt,
        nextReviewAt = nextReviewAt,
        snoozedUntil = snoozedUntil,
    )
}

data class ReviewSnapshot(
    val status: ItemStatus,
    val lastReviewedAt: Instant?,
    val nextReviewAt: Instant?,
    val snoozedUntil: Instant?,
)

/**
 * The result of a persisted review action. [previous] is captured inside the
 * same transaction as the write, so undo never relies on a stale UI item.
 */
data class ReviewChange(
    val item: SavedItem,
    val previous: ReviewSnapshot,
) {
    fun undo(): ReviewUndo? {
        val current = item.reviewSnapshot()
        if (current == previous) return null
        return ReviewUndo(itemId = item.id, previous = previous, expected = current)
    }
}

/** A one-step undo that is only valid while the item still has [expected] state. */
data class ReviewUndo(
    val itemId: String,
    val previous: ReviewSnapshot,
    val expected: ReviewSnapshot,
) {
    fun isApplicableTo(item: SavedItem): Boolean = item.reviewSnapshot() == expected
}

/** A transient, user-facing action result. [undo] is null for non-reversible actions. */
data class ReviewNotice(
    val text: String,
    val undo: ReviewUndo? = null,
)

enum class ItemStatus {
    NEW,
    READY,
    SNOOZED,
    USEFUL,
    ARCHIVED,
}

enum class SurfaceHint {
    /** The default: settle for a day, then join the daily batch. */
    QUEUE,

    /** Skip the settle delay and surface in the next batch. */
    TODAY,

    /** Never surface again; the user did not want it. */
    MUTED,
}

enum class MetadataStatus {
    PENDING,
    SUCCESS,
    FAILED,
}

enum class ReviewAction {
    OPENED,
    SNOOZED,
    MARKED_USEFUL,
    ARCHIVED,
    RESTORED,
    UNDO,
}

data class ReviewEvent(
    val id: Long,
    val itemId: String,
    val action: ReviewAction,
    val timestamp: Instant,
    val snoozeUntil: Instant?,
)

/** Best-effort preview data extracted from a page. All fields are optional. */
data class ItemMetadata(
    val title: String? = null,
    val description: String? = null,
    val imageUrl: String? = null,
)

data class ArchiveSnapshot(
    val items: List<SavedItem>,
    val events: List<ReviewEvent>,
)

data class RestoreWriteResult(
    val insertedItems: Int,
    val skippedItems: Int,
    val insertedEvents: Int,
)

enum class ArchiveSort {
    /** Smart ranking: deadline urgency, never-opened, longest ignored. */
    SMART,
    NEWEST,
    OLDEST,
    RECENTLY_REVIEWED,
}
