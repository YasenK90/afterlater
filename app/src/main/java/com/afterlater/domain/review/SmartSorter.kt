package com.afterlater.domain.review

import com.afterlater.core.model.SavedItem
import java.time.Instant

/**
 * The shared ranking that orders both the daily batch and the smart archive
 * view. Tiered rather than weighted, so the order is always explainable:
 *
 * 1. **Deadline urgency** — entries with a live deadline come first, soonest
 *    deadline leading. "Buy it before tonight" beats variety and age.
 * 2. **Never-opened** — saved-but-never-opened links outrank reviewed ones so
 *    they cannot rot unseen.
 * 3. **Longest ignored** — within a tier, the entry touched least recently
 *    leads: `lastReviewedAt` for reviewed entries, `savedAt` for untouched
 *    ones.
 *
 * Ties break by id for a deterministic order. This object is pure; the
 * daily-batch caller applies domain diversity on top, and the archive calls
 * it directly on the fetched rows.
 */
object SmartSorter {

    fun rank(items: List<SavedItem>): List<SavedItem> =
        items.sortedWith(
            compareBy(
                { it.expiresAt ?: Instant.MAX },
                { if (it.lastReviewedAt == null) 0 else 1 },
                { it.lastReviewedAt ?: it.savedAt },
                { it.id },
            ),
        )
}
