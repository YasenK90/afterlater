package com.afterlater.feature.archive

import com.afterlater.core.model.ArchiveSnapshot
import com.afterlater.core.model.ArchiveSort
import com.afterlater.core.model.CaptureResult
import com.afterlater.core.model.ItemMetadata
import com.afterlater.core.model.ItemStatus
import com.afterlater.core.model.MetadataStatus
import com.afterlater.core.model.ParsedLink
import com.afterlater.core.model.ReviewChange
import com.afterlater.core.model.ReviewEvent
import com.afterlater.core.model.ReviewUndo
import com.afterlater.core.model.RestoreWriteResult
import com.afterlater.core.model.SavedItem
import com.afterlater.core.model.SurfaceHint
import com.afterlater.data.repository.SavedItemRepository
import com.afterlater.domain.review.ExpiryChoice
import com.afterlater.domain.review.SnoozeWindow
import com.afterlater.test.MainDispatcherRule
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ArchiveViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `search is trimmed and debounced before querying`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeArchiveRepository()
            val viewModel = ArchiveViewModel(repository)
            collectArchive(viewModel)

            viewModel.setQuery("  neural nets  ")
            advanceTimeBy(100)
            assertEquals("", repository.lastQuery)

            advanceTimeBy(81)
            runCurrent()
            assertEquals("neural nets", repository.lastQuery)
        }

    @Test
    fun `status filters reach the repository and clearing resets them`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeArchiveRepository()
            val viewModel = ArchiveViewModel(repository)
            collectArchive(viewModel)

            viewModel.setStatus(ArchiveStatusFilter.IN_ROTATION)
            advanceUntilIdle()
            assertEquals(
                setOf(ItemStatus.NEW, ItemStatus.READY, ItemStatus.SNOOZED),
                repository.lastStatuses,
            )

            viewModel.setStatus(ArchiveStatusFilter.USEFUL)
            advanceUntilIdle()
            assertEquals(setOf(ItemStatus.USEFUL), repository.lastStatuses)

            viewModel.setStatus(ArchiveStatusFilter.ARCHIVED)
            viewModel.setDomain("example.com")
            viewModel.setQuery("  saved note  ")
            advanceUntilIdle()
            assertEquals(setOf(ItemStatus.ARCHIVED), repository.lastStatuses)
            assertEquals("example.com", repository.lastDomain)
            assertEquals("saved note", repository.lastQuery)
            assertEquals(ArchiveSort.SMART, repository.lastSort)

            viewModel.setTag("Recipe")
            advanceUntilIdle()
            assertEquals("Recipe", repository.lastTag)

            viewModel.clearFilters()
            advanceUntilIdle()
            assertNull(repository.lastStatuses)
            assertNull(repository.lastDomain)
            assertNull(repository.lastTag)
            assertEquals("", repository.lastQuery)
        }

    @Test
    fun `restore calls the repository and reports success`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeArchiveRepository().apply {
                restoreResult = ReviewChange(
                    item = savedItem(status = ItemStatus.READY),
                    previous = savedItem(status = ItemStatus.ARCHIVED).reviewSnapshot(),
                )
            }
            val viewModel = ArchiveViewModel(repository)

            viewModel.restore("item-1")
            advanceUntilIdle()

            assertEquals("item-1", repository.restoredId)
            assertEquals("RESTORED TO TODAY.", viewModel.message.value)
            assertFalse(viewModel.busy.value)
        }

    @Test
    fun `a second restore tap is ignored while the first is running`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeArchiveRepository()
            val viewModel = ArchiveViewModel(repository)

            viewModel.restore("item-1")
            viewModel.restore("item-1")
            advanceUntilIdle()

            assertEquals(1, repository.restoreCalls)
        }

    @Test
    fun `delete reports success and failure`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeArchiveRepository()
            val viewModel = ArchiveViewModel(repository)

            viewModel.delete("item-1")
            advanceUntilIdle()
            assertEquals("item-1", repository.deletedId)
            assertEquals("DELETED PERMANENTLY.", viewModel.message.value)

            viewModel.clearMessage()
            repository.deleteResult = false
            viewModel.delete("item-2")
            advanceUntilIdle()
            assertEquals("ENTRY NOT FOUND.", viewModel.message.value)
        }

    private fun TestScope.collectArchive(viewModel: ArchiveViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.items.collect {}
        }
        advanceUntilIdle()
    }
}

private class FakeArchiveRepository : SavedItemRepository {

    var lastQuery: String? = null
    var lastStatuses: Set<ItemStatus>? = null
    var lastDomain: String? = null
    var lastTag: String? = null
    var lastSort: ArchiveSort? = null

    var restoreCalls = 0
    var restoredId: String? = null
    var restoreResult: ReviewChange? = null

    var deletedId: String? = null
    var deleteResult = true

    override fun observeArchive(
        query: String,
        statuses: Set<ItemStatus>?,
        domain: String?,
        tag: String?,
        sort: ArchiveSort,
    ): Flow<List<SavedItem>> {
        lastQuery = query
        lastStatuses = statuses
        lastDomain = domain
        lastTag = tag
        lastSort = sort
        return flowOf(listOf(savedItem(status = statuses?.singleOrNull() ?: ItemStatus.ARCHIVED)))
    }

    override fun observeTags(): Flow<List<String>> = flowOf(listOf("Recipe", "Useful"))

    override fun observeTagsForItem(itemId: String): Flow<List<String>> = flowOf(emptyList())

    override fun observeDomains(): Flow<List<String>> = flowOf(listOf("example.com"))

    override fun observeTotalCount(): Flow<Int> = flowOf(1)

    override suspend fun restore(id: String): ReviewChange? {
        restoreCalls += 1
        restoredId = id
        return restoreResult
    }

    override suspend fun delete(id: String): Boolean {
        deletedId = id
        return deleteResult
    }

    override fun observeItem(id: String): Flow<SavedItem?> = flowOf(null)

    override fun observeDueCount(nowMillis: Long): Flow<Int> = flowOf(0)

    override fun observeDueCandidates(nowMillis: Long, limit: Int): Flow<List<SavedItem>> = flowOf(emptyList())

    override suspend fun getItem(id: String): SavedItem? = null

    override suspend fun findDueCandidates(nowMillis: Long, limit: Int): List<SavedItem> = emptyList()

    override suspend fun capture(link: ParsedLink): CaptureResult = error("not used")

    override suspend fun openItem(id: String): ReviewChange? = error("not used")

    override suspend fun snooze(id: String, window: SnoozeWindow): ReviewChange? = error("not used")

    override suspend fun markUseful(id: String): ReviewChange? = error("not used")

    override suspend fun archive(id: String): ReviewChange? = error("not used")

    override suspend fun undoReview(undo: ReviewUndo): ReviewChange? = error("not used")

    override suspend fun setSurfaceHint(id: String, hint: SurfaceHint): SavedItem? = error("not used")

    override suspend fun setExpiry(id: String, expiresAt: Instant?): SavedItem? = error("not used")

    override suspend fun setDeadline(id: String, choice: ExpiryChoice): SavedItem? = error("not used")

    override suspend fun applyTags(itemId: String, tags: List<String>) = Unit

    override suspend fun addTags(itemId: String, tags: List<String>) = Unit

    override suspend fun updateNote(id: String, note: String?): SavedItem? = error("not used")

    override suspend fun applyMetadata(id: String, metadata: ItemMetadata) = Unit

    override suspend fun markMetadataPending(id: String) = Unit

    override suspend fun markMetadataFailed(id: String) = Unit

    override suspend fun retryMetadata(id: String) = Unit

    override suspend fun exportSnapshot(): ArchiveSnapshot = error("not used")

    override suspend fun restoreBackup(
        items: List<SavedItem>,
        events: List<ReviewEvent>,
    ): RestoreWriteResult = error("not used")

    override suspend fun markUsefulAndReschedule(id: String): ReviewChange? = error("not used")
}

private fun savedItem(
    id: String = "item-1",
    status: ItemStatus = ItemStatus.ARCHIVED,
) = SavedItem(
    id = id,
    originalUrl = "https://example.com/article",
    canonicalUrl = "https://example.com/article",
    title = "Example article",
    description = "A saved article",
    domain = "example.com",
    thumbnailUrl = null,
    savedAt = Instant.parse("2026-01-01T08:00:00Z"),
    updatedAt = Instant.parse("2026-01-02T08:00:00Z"),
    status = status,
    lastReviewedAt = null,
    nextReviewAt = null,
    snoozedUntil = null,
    personalNote = "Important reference",
    metadataStatus = MetadataStatus.SUCCESS,
)
