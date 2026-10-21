package com.afterlater.data.repository

import com.afterlater.core.model.ArchiveSnapshot
import com.afterlater.core.model.ArchiveSort
import com.afterlater.core.model.CaptureResult
import com.afterlater.core.model.ItemMetadata
import com.afterlater.core.model.ItemStatus
import com.afterlater.core.model.ParsedLink
import com.afterlater.core.model.ReviewChange
import com.afterlater.core.model.ReviewUndo
import com.afterlater.core.model.SavedItem
import com.afterlater.domain.review.SnoozeWindow
import kotlinx.coroutines.flow.Flow

interface SavedItemRepository {
    fun observeArchive(query: String, status: ItemStatus?, domain: String?, sort: ArchiveSort): Flow<List<SavedItem>>
    fun observeItem(id: String): Flow<SavedItem?>
    fun observeDueCount(nowMillis: Long): Flow<Int>
    fun observeDueCandidates(nowMillis: Long, limit: Int): Flow<List<SavedItem>>
    fun observeTotalCount(): Flow<Int>
    fun observeDomains(): Flow<List<String>>

    suspend fun getItem(id: String): SavedItem?
    suspend fun findDueCandidates(nowMillis: Long, limit: Int): List<SavedItem>
    suspend fun capture(link: ParsedLink): CaptureResult
    suspend fun openItem(id: String): ReviewChange?
    suspend fun snooze(id: String, window: SnoozeWindow): ReviewChange?
    suspend fun markUseful(id: String): ReviewChange?
    suspend fun archive(id: String): ReviewChange?
    suspend fun restore(id: String): ReviewChange?
    suspend fun undoReview(undo: ReviewUndo): ReviewChange?
    suspend fun updateNote(id: String, note: String?): SavedItem?
    suspend fun delete(id: String): Boolean
    suspend fun applyMetadata(id: String, metadata: ItemMetadata)
    suspend fun markMetadataPending(id: String)
    suspend fun markMetadataFailed(id: String)
    suspend fun retryMetadata(id: String)
    suspend fun exportSnapshot(): ArchiveSnapshot
}
