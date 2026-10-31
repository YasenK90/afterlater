package com.afterlater.data.repository

import com.afterlater.core.model.ArchiveSnapshot
import com.afterlater.core.model.ArchiveSort
import com.afterlater.core.model.CaptureResult
import com.afterlater.core.model.ItemMetadata
import com.afterlater.core.model.ItemStatus
import com.afterlater.core.model.ParsedLink
import com.afterlater.core.model.ReviewChange
import com.afterlater.core.model.ReviewEvent
import com.afterlater.core.model.ReviewUndo
import com.afterlater.core.model.RestoreWriteResult
import com.afterlater.core.model.SavedItem
import com.afterlater.core.model.SurfaceHint
import com.afterlater.domain.review.ExpiryChoice
import com.afterlater.domain.review.SnoozeWindow
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface SavedItemRepository {
    fun observeArchive(
        query: String,
        statuses: Set<ItemStatus>?,
        domain: String?,
        tag: String?,
        sort: ArchiveSort,
    ): Flow<List<SavedItem>>
    fun observeItem(id: String): Flow<SavedItem?>
    fun observeDueCount(nowMillis: Long): Flow<Int>
    fun observeDueCandidates(nowMillis: Long, limit: Int): Flow<List<SavedItem>>
    fun observeTotalCount(): Flow<Int>
    fun observeDomains(): Flow<List<String>>
    fun observeTags(): Flow<List<String>>
    fun observeTagsForItem(itemId: String): Flow<List<String>>

    suspend fun getItem(id: String): SavedItem?
    suspend fun findDueCandidates(nowMillis: Long, limit: Int): List<SavedItem>
    suspend fun capture(link: ParsedLink): CaptureResult
    suspend fun openItem(id: String): ReviewChange?
    suspend fun snooze(id: String, window: SnoozeWindow): ReviewChange?
    suspend fun markUseful(id: String): ReviewChange?

    /**
     * Marks the item useful while keeping it in the review rotation: the next
     * review is re-armed instead of being cleared the way [markUseful] does.
     */
    suspend fun markUsefulAndReschedule(id: String): ReviewChange?
    suspend fun archive(id: String): ReviewChange?
    suspend fun restore(id: String): ReviewChange?
    suspend fun undoReview(undo: ReviewUndo): ReviewChange?
    suspend fun setSurfaceHint(id: String, hint: SurfaceHint): SavedItem?
    suspend fun setExpiry(id: String, expiresAt: Instant?): SavedItem?
    suspend fun setDeadline(id: String, choice: ExpiryChoice): SavedItem?
    suspend fun applyTags(itemId: String, tags: List<String>)
    suspend fun addTags(itemId: String, tags: List<String>)
    suspend fun updateNote(id: String, note: String?): SavedItem?
    suspend fun delete(id: String): Boolean
    suspend fun applyMetadata(id: String, metadata: ItemMetadata)
    suspend fun markMetadataPending(id: String)
    suspend fun markMetadataFailed(id: String)
    suspend fun retryMetadata(id: String)
    suspend fun exportSnapshot(): ArchiveSnapshot
    suspend fun restoreBackup(items: List<SavedItem>, events: List<ReviewEvent>): RestoreWriteResult
}
