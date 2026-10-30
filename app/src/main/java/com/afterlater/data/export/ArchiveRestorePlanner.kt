package com.afterlater.data.export

import com.afterlater.core.model.ArchiveSnapshot
import com.afterlater.core.model.ReviewEvent
import com.afterlater.core.model.SavedItem
import java.time.Instant

data class ArchiveRestorePlan(
    val backupExportedAt: Instant,
    val backupItemCount: Int,
    val newItems: List<SavedItem>,
    val newEvents: List<ReviewEvent>,
    val duplicateCount: Int,
    val skippedEventCount: Int,
) {
    val newItemCount: Int get() = newItems.size
}

object ArchiveRestorePlanner {

    fun plan(backup: ArchiveBackup, existing: ArchiveSnapshot): ArchiveRestorePlan {
        val knownIds = existing.items.mapTo(HashSet()) { it.id }
        val knownCanonicalUrls = existing.items.mapTo(HashSet()) { it.canonicalUrl }
        val newItems = ArrayList<SavedItem>()
        var duplicates = 0

        for (item in backup.items) {
            if (item.id in knownIds || item.canonicalUrl in knownCanonicalUrls) {
                duplicates += 1
                continue
            }
            knownIds += item.id
            knownCanonicalUrls += item.canonicalUrl
            newItems += item
        }

        val newIds = newItems.mapTo(HashSet()) { it.id }
        val newEvents = backup.events
            .filter { it.itemId in newIds }
            .map { it.copy(id = 0) }
        val skippedEvents = backup.events.size - newEvents.size

        return ArchiveRestorePlan(
            backupExportedAt = backup.exportedAt,
            backupItemCount = backup.items.size,
            newItems = newItems,
            newEvents = newEvents,
            duplicateCount = duplicates,
            skippedEventCount = skippedEvents,
        )
    }
}
