package com.afterlater.feature.capture

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
import com.afterlater.core.model.SharedContent
import com.afterlater.core.model.SurfaceHint
import com.afterlater.data.repository.SavedItemRepository
import com.afterlater.domain.review.ExpiryChoice
import com.afterlater.domain.review.SnoozeWindow
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ShareViewModelTest {

    @Test
    fun `a shared url is saved immediately with its parsed identity`() =
        runTest {
            val repository = FakeShareRepository().apply {
                captureResult = CaptureResult.Saved(savedItem())
            }
            val viewModel = ShareViewModel(repository, this)

            viewModel.handleContent(
                SharedContent(
                    text = "Worth reading: https://example.com/watch?utm_source=newsletter",
                    subject = "A shared title",
                ),
            )
            advanceUntilIdle()

            assertTrue(viewModel.state.value is ShareCaptureUiState.Saved)
            assertEquals("https://example.com/watch?utm_source=newsletter", repository.captured?.originalUrl)
            assertEquals("https://example.com/watch", repository.captured?.canonicalUrl)
            assertEquals("A shared title", repository.captured?.title)
        }

    @Test
    fun `missing or invalid content produces clear feedback without capturing`() =
        runTest {
            val repository = FakeShareRepository()
            val viewModel = ShareViewModel(repository, this)

            viewModel.handleContent(null)
            val empty = viewModel.state.value as ShareCaptureUiState.Error
            assertEquals("NOTHING TO SAVE", empty.heading)

            viewModel.handleContent(SharedContent("No link in this share", null))
            val invalid = viewModel.state.value as ShareCaptureUiState.Error
            assertEquals("NO LINK FOUND", invalid.heading)
            assertEquals(0, repository.captureCalls)
        }

    @Test
    fun `duplicate and failed captures surface the repository result`() =
        runTest {
            val repository = FakeShareRepository().apply {
                captureResult = CaptureResult.Duplicate(savedItem())
            }
            val viewModel = ShareViewModel(repository, this)

            viewModel.handleContent(SharedContent("https://example.com/a", null))
            advanceUntilIdle()
            assertTrue(viewModel.state.value is ShareCaptureUiState.Duplicate)

            repository.captureResult = CaptureResult.Failed("The local archive could not be written.")
            viewModel.handleContent(SharedContent("https://example.com/b", null))
            advanceUntilIdle()
            val failed = viewModel.state.value as ShareCaptureUiState.Error
            assertEquals("NOT SAVED", failed.heading)
            assertEquals("The local archive could not be written.", failed.message)
        }

    @Test
    fun `an unexpected repository failure never leaves the screen saving`() =
        runTest {
            val repository = FakeShareRepository().apply {
                throwOnCapture = true
            }
            val viewModel = ShareViewModel(repository, this)

            viewModel.handleContent(SharedContent("https://example.com/a", null))
            advanceUntilIdle()

            val error = viewModel.state.value as ShareCaptureUiState.Error
            assertEquals("NOT SAVED", error.heading)
        }

    @Test
    fun `the same canonical link is not captured twice while saving`() =
        runTest {
            val gate = CompletableDeferred<CaptureResult>()
            val repository = FakeShareRepository().apply {
                captureGate = gate
            }
            val viewModel = ShareViewModel(repository, this)

            viewModel.handleContent(SharedContent("https://example.com/a", null))
            advanceUntilIdle()
            assertTrue(viewModel.state.value is ShareCaptureUiState.Saving)

            viewModel.handleContent(
                SharedContent("https://example.com/a?utm_source=duplicate", null),
            )
            advanceUntilIdle()
            assertEquals(1, repository.captureCalls)

            gate.complete(CaptureResult.Saved(savedItem()))
            advanceUntilIdle()
            assertTrue(viewModel.state.value is ShareCaptureUiState.Saved)
        }

    @Test
    fun `an older save cannot replace the feedback for a newer share`() =
        runTest {
            val first = CompletableDeferred<CaptureResult>()
            val second = CompletableDeferred<CaptureResult>()
            val repository = FakeShareRepository().apply {
                captureGateFor = { link ->
                    if (link.canonicalUrl.endsWith("/a")) first else second
                }
            }
            val viewModel = ShareViewModel(repository, this)

            viewModel.handleContent(SharedContent("https://example.com/a", null))
            advanceUntilIdle()
            viewModel.handleContent(SharedContent("https://example.com/b", null))
            advanceUntilIdle()
            assertEquals(2, repository.captureCalls)

            first.complete(CaptureResult.Saved(savedItem()))
            advanceUntilIdle()
            assertTrue(viewModel.state.value is ShareCaptureUiState.Saving)

            second.complete(CaptureResult.Saved(savedItem()))
            advanceUntilIdle()
            assertTrue(viewModel.state.value is ShareCaptureUiState.Saved)
        }
}

private class FakeShareRepository : SavedItemRepository {

    var captureCalls = 0
    var captured: ParsedLink? = null
    var captureResult: CaptureResult = CaptureResult.Failed("not configured")
    var captureGate: CompletableDeferred<CaptureResult>? = null
    var captureGateFor: ((ParsedLink) -> CompletableDeferred<CaptureResult>?)? = null
    var throwOnCapture = false

    override suspend fun capture(link: ParsedLink): CaptureResult {
        captureCalls += 1
        captured = link
        if (throwOnCapture) error("unexpected repository failure")
        return captureGateFor?.invoke(link)?.await() ?: captureGate?.await() ?: captureResult
    }

    override fun observeArchive(
        query: String,
        statuses: Set<ItemStatus>?,
        domain: String?,
        sort: ArchiveSort,
    ): Flow<List<SavedItem>> = flowOf(emptyList())

    override fun observeItem(id: String): Flow<SavedItem?> = flowOf(null)

    override fun observeDueCount(nowMillis: Long): Flow<Int> = flowOf(0)

    override fun observeDueCandidates(nowMillis: Long, limit: Int): Flow<List<SavedItem>> = flowOf(emptyList())

    override fun observeTotalCount(): Flow<Int> = flowOf(0)

    override fun observeDomains(): Flow<List<String>> = flowOf(emptyList())

    override suspend fun getItem(id: String): SavedItem? = null

    override suspend fun findDueCandidates(nowMillis: Long, limit: Int): List<SavedItem> = emptyList()

    override suspend fun openItem(id: String): ReviewChange? = error("not used")

    override suspend fun snooze(id: String, window: SnoozeWindow): ReviewChange? = error("not used")

    override suspend fun markUseful(id: String): ReviewChange? = error("not used")

    override suspend fun archive(id: String): ReviewChange? = error("not used")

    override suspend fun restore(id: String): ReviewChange? = error("not used")

    override suspend fun undoReview(undo: ReviewUndo): ReviewChange? = error("not used")

    override suspend fun setSurfaceHint(id: String, hint: SurfaceHint): SavedItem? = error("not used")

    override suspend fun setExpiry(id: String, expiresAt: Instant?): SavedItem? = error("not used")

    override suspend fun setDeadline(id: String, choice: ExpiryChoice): SavedItem? = error("not used")

    override suspend fun applyTags(itemId: String, tags: List<String>) = Unit

    override suspend fun updateNote(id: String, note: String?): SavedItem? = error("not used")

    override suspend fun delete(id: String): Boolean = false

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

private fun savedItem() = SavedItem(
    id = "item-1",
    originalUrl = "https://example.com/a",
    canonicalUrl = "https://example.com/a",
    title = "Saved link",
    description = null,
    domain = "example.com",
    thumbnailUrl = null,
    savedAt = Instant.parse("2026-01-01T08:00:00Z"),
    updatedAt = Instant.parse("2026-01-01T08:00:00Z"),
    status = ItemStatus.NEW,
    lastReviewedAt = null,
    nextReviewAt = null,
    snoozedUntil = null,
    personalNote = null,
    metadataStatus = MetadataStatus.PENDING,
)
