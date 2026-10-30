package com.afterlater.test

import com.afterlater.core.model.ArchiveSnapshot
import com.afterlater.core.model.ArchiveSort
import com.afterlater.core.model.CaptureResult
import com.afterlater.core.model.ItemMetadata
import com.afterlater.core.model.ItemStatus
import com.afterlater.core.model.NoteText
import com.afterlater.core.model.ParsedLink
import com.afterlater.core.model.ReviewChange
import com.afterlater.core.model.ReviewEvent
import com.afterlater.core.model.ReviewUndo
import com.afterlater.core.model.RestoreWriteResult
import com.afterlater.core.model.SavedItem
import com.afterlater.core.model.SurfaceHint
import com.afterlater.data.repository.SavedItemRepository
import com.afterlater.domain.review.ExpiryChoice
import com.afterlater.domain.review.ReviewScheduler
import com.afterlater.domain.review.SnoozeWindow
import com.afterlater.domain.review.resolve
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class FakeSavedItemRepository(
    initialItem: SavedItem? = null,
) : SavedItemRepository {

    val itemFlow = MutableStateFlow(initialItem)
    val dueItems = MutableStateFlow<List<SavedItem>>(emptyList())
    val totalCount = MutableStateFlow(0)
    var captureCalls: Int = 0
    var capturedLink: ParsedLink? = null
    var captureError: Exception? = null
    var captureResult: CaptureResult = CaptureResult.Failed("Not configured.")
    var updateNoteHandler: (suspend (String, String?) -> SavedItem?)? = null
    var openResult: ReviewChange? = null
    var snoozeResult: ReviewChange? = null
    var markUsefulResult: ReviewChange? = null
    var markUsefulAndRescheduleResult: ReviewChange? = null
    var archiveResult: ReviewChange? = null
    var restoreResult: ReviewChange? = null
    var undoResult: ReviewChange? = null
    var deleteResult: Boolean = false
    var lastDeadlineChoice: ExpiryChoice? = null

    override fun observeArchive(
        query: String,
        statuses: Set<ItemStatus>?,
        domain: String?,
        tag: String?,
        sort: ArchiveSort,
    ): Flow<List<SavedItem>> = flowOf(emptyList())

    override fun observeItem(id: String): Flow<SavedItem?> = itemFlow

    override fun observeDueCount(nowMillis: Long): Flow<Int> = dueItems.map { items ->
        val now = Instant.ofEpochMilli(nowMillis)
        items.count { ReviewScheduler.isEligible(it, now) }
    }

    override fun observeDueCandidates(nowMillis: Long, limit: Int): Flow<List<SavedItem>> = dueItems

    override fun observeTotalCount(): Flow<Int> = totalCount

    override fun observeDomains(): Flow<List<String>> = flowOf(emptyList())

    override suspend fun getItem(id: String): SavedItem? = itemFlow.value

    override suspend fun findDueCandidates(nowMillis: Long, limit: Int): List<SavedItem> = dueItems.value

    override suspend fun capture(link: ParsedLink): CaptureResult {
        captureCalls += 1
        capturedLink = link
        captureError?.let { throw it }
        return captureResult
    }

    override suspend fun openItem(id: String): ReviewChange? = openResult

    override suspend fun snooze(id: String, window: SnoozeWindow): ReviewChange? = snoozeResult

    override suspend fun markUseful(id: String): ReviewChange? = markUsefulResult

    override suspend fun markUsefulAndReschedule(id: String): ReviewChange? = markUsefulAndRescheduleResult

    override suspend fun archive(id: String): ReviewChange? = archiveResult

    override suspend fun restore(id: String): ReviewChange? = restoreResult

    override suspend fun undoReview(undo: ReviewUndo): ReviewChange? = undoResult

    override suspend fun setSurfaceHint(id: String, hint: SurfaceHint): SavedItem? {
        val current = itemFlow.value ?: return null
        val updated = current.copy(surfaceHint = hint)
        itemFlow.value = updated
        return updated
    }

    override suspend fun setExpiry(id: String, expiresAt: Instant?): SavedItem? {
        val current = itemFlow.value ?: return null
        val updated = current.copy(expiresAt = expiresAt)
        itemFlow.value = updated
        return updated
    }

    override suspend fun setDeadline(id: String, choice: ExpiryChoice): SavedItem? {
        lastDeadlineChoice = choice
        val resolved = choice.resolve(DEADLINE_NOW, ZONE)
        return setExpiry(id, resolved)
    }

    val appliedTags = mutableMapOf<String, List<String>>()
    val allTags = MutableStateFlow<List<String>>(emptyList())
    val itemTags = MutableStateFlow<Map<String, List<String>>>(emptyMap())

    override suspend fun applyTags(itemId: String, tags: List<String>) {
        appliedTags[itemId] = tags
        itemTags.value = itemTags.value + (itemId to tags)
        allTags.value = (allTags.value + tags).distinct().sorted()
    }

    override fun observeTags(): Flow<List<String>> = allTags

    override fun observeTagsForItem(itemId: String): Flow<List<String>> =
        itemTags.map { it[itemId].orEmpty() }

    override suspend fun updateNote(id: String, note: String?): SavedItem? {
        updateNoteHandler?.let { return it(id, note) }
        val current = itemFlow.value ?: return null
        val updated = current.copy(personalNote = NoteText.normalize(note))
        itemFlow.value = updated
        return updated
    }

    override suspend fun delete(id: String): Boolean = deleteResult

    override suspend fun applyMetadata(id: String, metadata: ItemMetadata) = Unit

    override suspend fun markMetadataPending(id: String) = Unit

    override suspend fun markMetadataFailed(id: String) = Unit

    override suspend fun retryMetadata(id: String) = Unit

    override suspend fun exportSnapshot(): ArchiveSnapshot =
        ArchiveSnapshot(items = emptyList(), events = emptyList())

    override suspend fun restoreBackup(
        items: List<SavedItem>,
        events: List<ReviewEvent>,
    ): RestoreWriteResult = RestoreWriteResult(0, 0, 0)

    private companion object {
        val DEADLINE_NOW: Instant = Instant.parse("2026-03-10T09:00:00Z")
        val ZONE: ZoneId = ZoneId.of("UTC")
    }
}
