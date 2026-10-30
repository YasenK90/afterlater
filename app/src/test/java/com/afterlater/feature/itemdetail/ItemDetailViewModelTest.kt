package com.afterlater.feature.itemdetail

import com.afterlater.core.model.ItemStatus
import com.afterlater.core.model.ReviewChange
import com.afterlater.domain.review.ExpiryChoice
import com.afterlater.test.FakeSavedItemRepository
import com.afterlater.test.MainDispatcherRule
import com.afterlater.test.savedItem
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ItemDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `failed note save does not navigate and keeps the draft dirty`() =
        runTest(mainDispatcherRule.dispatcher) {
            val item = savedItem()
            val repository = FakeSavedItemRepository(item).apply {
                updateNoteHandler = { _, _ -> null }
            }
            val viewModel = ItemDetailViewModel(item.id, repository)
            advanceUntilIdle()

            viewModel.onNoteChange("A reason to come back.")

            var navigated = false
            viewModel.saveNote { navigated = true }
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(navigated)
            assertTrue(state.noteDirty)
            assertEquals("A reason to come back.", state.noteDraft)
            assertEquals("Note could not be saved.", state.message?.text)
        }

    @Test
    fun `successful note save clears the draft and navigates back`() =
        runTest(mainDispatcherRule.dispatcher) {
            val item = savedItem()
            val repository = FakeSavedItemRepository(item)
            val viewModel = ItemDetailViewModel(item.id, repository)
            advanceUntilIdle()

            viewModel.onNoteChange("Worth a second look.")

            var navigated = false
            viewModel.saveNote { navigated = true }
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(navigated)
            assertFalse(state.noteDirty)
            assertEquals("Worth a second look.", repository.itemFlow.value?.personalNote)
            assertEquals("Note saved.", state.message?.text)
        }

    @Test
    fun `opening an archived item does not claim it returns in seven days`() =
        runTest(mainDispatcherRule.dispatcher) {
            val archived = savedItem(status = ItemStatus.ARCHIVED)
            val repository = FakeSavedItemRepository(archived).apply {
                openResult = ReviewChange(
                    item = archived.copy(lastReviewedAt = archived.savedAt),
                    previous = archived.reviewSnapshot(),
                )
            }
            val viewModel = ItemDetailViewModel(archived.id, repository)
            advanceUntilIdle()

            viewModel.recordOpen()
            advanceUntilIdle()

            assertEquals(
                "OPENED. STILL OUT OF ROTATION.",
                viewModel.uiState.value.message?.text,
            )
        }

    @Test
    fun `a missing item reports failure instead of a successful action`() =
        runTest(mainDispatcherRule.dispatcher) {
            val item = savedItem()
            val repository = FakeSavedItemRepository(item).apply {
                markUsefulResult = null
            }
            val viewModel = ItemDetailViewModel(item.id, repository)
            advanceUntilIdle()

            viewModel.markUseful()
            advanceUntilIdle()

            assertEquals("ENTRY NOT FOUND.", viewModel.uiState.value.message?.text)
        }

    @Test
    fun `clearing a deadline removes it and confirms`() =
        runTest(mainDispatcherRule.dispatcher) {
            val item = savedItem().copy(expiresAt = Instant.parse("2026-03-11T00:00:00Z"))
            val repository = FakeSavedItemRepository(item)
            val viewModel = ItemDetailViewModel(item.id, repository)
            advanceUntilIdle()

            viewModel.setDeadline(ExpiryChoice.NONE)
            advanceUntilIdle()

            assertNull(repository.itemFlow.value?.expiresAt)
            assertEquals("DEADLINE CLEARED.", viewModel.uiState.value.message?.text)
        }

    @Test
    fun `setting a deadline stores one and confirms`() =
        runTest(mainDispatcherRule.dispatcher) {
            val item = savedItem()
            val repository = FakeSavedItemRepository(item)
            val viewModel = ItemDetailViewModel(item.id, repository)
            advanceUntilIdle()

            viewModel.setDeadline(ExpiryChoice.TODAY)
            advanceUntilIdle()

            assertEquals(ExpiryChoice.TODAY, repository.lastDeadlineChoice)
            assertNotNull(repository.itemFlow.value?.expiresAt)
            assertEquals("DEADLINE SET.", viewModel.uiState.value.message?.text)
        }
}
