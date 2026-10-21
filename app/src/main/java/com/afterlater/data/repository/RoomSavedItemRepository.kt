package com.afterlater.data.repository

import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteException
import android.util.Log
import androidx.room.withTransaction
import com.afterlater.core.database.AfterLaterDatabase
import com.afterlater.core.database.ArchiveSearch
import com.afterlater.core.database.ReviewEventEntity
import com.afterlater.core.database.SavedItemEntity
import com.afterlater.core.database.toModel
import com.afterlater.core.model.ArchiveSnapshot
import com.afterlater.core.model.ArchiveSort
import com.afterlater.core.model.CaptureResult
import com.afterlater.core.model.ItemMetadata
import com.afterlater.core.model.ItemStatus
import com.afterlater.core.model.MetadataStatus
import com.afterlater.core.model.NoteText
import com.afterlater.core.model.ParsedLink
import com.afterlater.core.model.ReviewAction
import com.afterlater.core.model.ReviewChange
import com.afterlater.core.model.ReviewUndo
import com.afterlater.core.model.SavedItem
import com.afterlater.data.metadata.MetadataScheduler
import com.afterlater.domain.review.ReviewScheduler
import com.afterlater.domain.review.SnoozeWindow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.util.UUID

class RoomSavedItemRepository(
    private val database: AfterLaterDatabase,
    private val metadataScheduler: MetadataScheduler,
    private val clock: Clock = Clock.systemUTC(),
) : SavedItemRepository {

    private val items = database.savedItemDao()
    private val events = database.reviewEventDao()

    override fun observeArchive(
        query: String,
        status: ItemStatus?,
        domain: String?,
        sort: ArchiveSort,
    ): Flow<List<SavedItem>> = items.observeArchive(
        query = ArchiveSearch.escapeLikePattern(query.trim()),
        status = status?.name,
        domain = domain,
        sort = sort.name,
    ).map { rows -> rows.map { it.toModel() } }

    override fun observeItem(id: String): Flow<SavedItem?> =
        items.observeById(id).map { it?.toModel() }

    override fun observeDueCount(nowMillis: Long): Flow<Int> = items.observeDueCount(nowMillis)

    override fun observeDueCandidates(nowMillis: Long, limit: Int): Flow<List<SavedItem>> =
        items.observeDueCandidates(nowMillis, limit).map { rows -> rows.map { it.toModel() } }

    override fun observeTotalCount(): Flow<Int> = items.observeTotalCount()

    override fun observeDomains(): Flow<List<String>> = items.observeDomains()

    override suspend fun getItem(id: String): SavedItem? = items.getById(id)?.toModel()

    override suspend fun findDueCandidates(nowMillis: Long, limit: Int): List<SavedItem> =
        items.getDueCandidates(nowMillis, limit).map { it.toModel() }

    override suspend fun capture(link: ParsedLink): CaptureResult {
        items.findByCanonicalUrl(link.canonicalUrl)?.let { existing ->
            return CaptureResult.Duplicate(existing.toModel())
        }

        val now = clock.instant()
        val entity = SavedItemEntity(
            id = UUID.randomUUID().toString(),
            originalUrl = link.originalUrl,
            canonicalUrl = link.canonicalUrl,
            title = link.title?.trim()?.take(300)?.takeIf { it.isNotEmpty() },
            description = null,
            domain = link.domain,
            thumbnailUrl = null,
            savedAt = now.toEpochMilli(),
            updatedAt = now.toEpochMilli(),
            status = ItemStatus.NEW,
            lastReviewedAt = null,
            nextReviewAt = ReviewScheduler.initialNextReviewAt(now).toEpochMilli(),
            snoozedUntil = null,
            personalNote = null,
            metadataStatus = MetadataStatus.PENDING,
        )

        return try {
            items.insert(entity)
            enqueueMetadataBestEffort(entity.id)
            CaptureResult.Saved(entity.toModel())
        } catch (_: SQLiteConstraintException) {
            val raced = items.findByCanonicalUrl(link.canonicalUrl)
                ?: return CaptureResult.Failed("The local archive could not be written.")
            CaptureResult.Duplicate(raced.toModel())
        } catch (_: SQLiteException) {
            CaptureResult.Failed("The local archive could not be written.")
        }
    }

    private suspend fun enqueueMetadataBestEffort(itemId: String) {
        try {
            metadataScheduler.enqueue(itemId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w("AFTERLATER", "Metadata enqueue failed for $itemId", error)
            markMetadataFailedBestEffort(itemId)
        }
    }

    private suspend fun markMetadataFailedBestEffort(itemId: String) {
        try {
            markMetadataFailed(itemId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w("AFTERLATER", "Metadata status update failed for $itemId", error)
        }
    }

    override suspend fun openItem(id: String): ReviewChange? = database.withTransaction {
        val current = items.getById(id) ?: return@withTransaction null
        val previous = current.toModel().reviewSnapshot()
        val now = clock.instant()
        val active = current.status == ItemStatus.NEW ||
            current.status == ItemStatus.READY ||
            current.status == ItemStatus.SNOOZED

        val updated = if (active) {
            current.copy(
                status = ItemStatus.READY,
                lastReviewedAt = now.toEpochMilli(),
                nextReviewAt = ReviewScheduler.nextAfterOpen(now).toEpochMilli(),
                snoozedUntil = null,
                updatedAt = now.toEpochMilli(),
            )
        } else {
            current.copy(lastReviewedAt = now.toEpochMilli(), updatedAt = now.toEpochMilli())
        }

        items.update(updated)
        events.insert(
            ReviewEventEntity(
                itemId = id,
                action = ReviewAction.OPENED,
                timestamp = now.toEpochMilli(),
                snoozeUntil = null,
            ),
        )
        ReviewChange(item = updated.toModel(), previous = previous)
    }

    override suspend fun snooze(id: String, window: SnoozeWindow): ReviewChange? = database.withTransaction {
        val current = items.getById(id) ?: return@withTransaction null
        val previous = current.toModel().reviewSnapshot()
        val now = clock.instant()
        val until = ReviewScheduler.snoozeUntil(now, window)

        val updated = current.copy(
            status = ItemStatus.SNOOZED,
            lastReviewedAt = now.toEpochMilli(),
            nextReviewAt = until.toEpochMilli(),
            snoozedUntil = until.toEpochMilli(),
            updatedAt = now.toEpochMilli(),
        )
        items.update(updated)
        events.insert(
            ReviewEventEntity(
                itemId = id,
                action = ReviewAction.SNOOZED,
                timestamp = now.toEpochMilli(),
                snoozeUntil = until.toEpochMilli(),
            ),
        )
        ReviewChange(item = updated.toModel(), previous = previous)
    }

    override suspend fun markUseful(id: String): ReviewChange? = transition(
        id = id,
        action = ReviewAction.MARKED_USEFUL,
        newStatus = ItemStatus.USEFUL,
    )

    override suspend fun archive(id: String): ReviewChange? = transition(
        id = id,
        action = ReviewAction.ARCHIVED,
        newStatus = ItemStatus.ARCHIVED,
    )

    override suspend fun restore(id: String): ReviewChange? = database.withTransaction {
        val current = items.getById(id) ?: return@withTransaction null
        val previous = current.toModel().reviewSnapshot()
        if (current.status == ItemStatus.NEW ||
            current.status == ItemStatus.READY ||
            current.status == ItemStatus.SNOOZED
        ) {
            return@withTransaction ReviewChange(item = current.toModel(), previous = previous)
        }

        val now = clock.instant()
        val updated = current.copy(
            status = ItemStatus.READY,
            nextReviewAt = now.toEpochMilli(),
            snoozedUntil = null,
            updatedAt = now.toEpochMilli(),
        )
        items.update(updated)
        events.insert(
            ReviewEventEntity(
                itemId = id,
                action = ReviewAction.RESTORED,
                timestamp = now.toEpochMilli(),
                snoozeUntil = null,
            ),
        )
        ReviewChange(item = updated.toModel(), previous = previous)
    }

    override suspend fun undoReview(undo: ReviewUndo): ReviewChange? = database.withTransaction {
        val current = items.getById(undo.itemId) ?: return@withTransaction null
        val currentModel = current.toModel()
        if (undo.expected == undo.previous || !undo.isApplicableTo(currentModel)) {
            return@withTransaction null
        }

        val now = clock.instant()
        val updated = current.copy(
            status = undo.previous.status,
            lastReviewedAt = undo.previous.lastReviewedAt?.toEpochMilli(),
            nextReviewAt = undo.previous.nextReviewAt?.toEpochMilli(),
            snoozedUntil = undo.previous.snoozedUntil?.toEpochMilli(),
            updatedAt = now.toEpochMilli(),
        )
        items.update(updated)
        events.insert(
            ReviewEventEntity(
                itemId = undo.itemId,
                action = ReviewAction.UNDO,
                timestamp = now.toEpochMilli(),
                snoozeUntil = null,
            ),
        )
        ReviewChange(item = updated.toModel(), previous = currentModel.reviewSnapshot())
    }

    private suspend fun transition(id: String, action: ReviewAction, newStatus: ItemStatus): ReviewChange? =
        database.withTransaction {
            val current = items.getById(id) ?: return@withTransaction null
            val previous = current.toModel().reviewSnapshot()
            if (current.status == newStatus) {
                return@withTransaction ReviewChange(item = current.toModel(), previous = previous)
            }

            val now = clock.instant()
            val updated = current.copy(
                status = newStatus,
                lastReviewedAt = now.toEpochMilli(),
                nextReviewAt = null,
                snoozedUntil = null,
                updatedAt = now.toEpochMilli(),
            )
            items.update(updated)
            events.insert(
                ReviewEventEntity(
                    itemId = id,
                    action = action,
                    timestamp = now.toEpochMilli(),
                    snoozeUntil = null,
                ),
            )
            ReviewChange(item = updated.toModel(), previous = previous)
        }

    override suspend fun updateNote(id: String, note: String?): SavedItem? {
        val current = items.getById(id) ?: return null
        val cleaned = NoteText.normalize(note)
        if (cleaned == current.personalNote) return current.toModel()
        val now = clock.instant()
        val updated = current.copy(personalNote = cleaned, updatedAt = now.toEpochMilli())
        items.update(updated)
        return updated.toModel()
    }

    override suspend fun delete(id: String): Boolean = database.withTransaction {
        val deleted = items.deleteById(id)
        events.deleteForItem(id)
        deleted > 0
    }

    override suspend fun applyMetadata(id: String, metadata: ItemMetadata) {
        val current = items.getById(id) ?: return
        if (current.metadataStatus == MetadataStatus.SUCCESS) return
        val now = clock.instant()
        val updated = current.copy(
            title = current.title?.takeIf { it.isNotBlank() } ?: metadata.title,
            description = current.description?.takeIf { it.isNotBlank() } ?: metadata.description,
            thumbnailUrl = current.thumbnailUrl?.takeIf { it.isNotBlank() } ?: metadata.imageUrl,
            metadataStatus = MetadataStatus.SUCCESS,
            updatedAt = now.toEpochMilli(),
        )
        items.update(updated)
    }

    override suspend fun markMetadataPending(id: String) {
        val current = items.getById(id) ?: return
        items.update(
            current.copy(
                metadataStatus = MetadataStatus.PENDING,
                updatedAt = clock.instant().toEpochMilli(),
            ),
        )
    }

    override suspend fun markMetadataFailed(id: String) {
        val current = items.getById(id) ?: return
        if (current.metadataStatus == MetadataStatus.SUCCESS) return
        items.update(
            current.copy(
                metadataStatus = MetadataStatus.FAILED,
                updatedAt = clock.instant().toEpochMilli(),
            ),
        )
    }

    override suspend fun retryMetadata(id: String) {
        val current = items.getById(id) ?: return
        if (current.metadataStatus == MetadataStatus.SUCCESS) return
        markMetadataPending(id)
        try {
            metadataScheduler.enqueue(id, replaceExisting = true)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            markMetadataFailed(id)
            throw error
        }
    }

    override suspend fun exportSnapshot(): ArchiveSnapshot {
        val items = items.getAll().map { it.toModel() }
        val events = events.getAll().map { it.toModel() }
        return ArchiveSnapshot(items = items, events = events)
    }
}
