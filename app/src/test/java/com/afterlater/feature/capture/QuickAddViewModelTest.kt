package com.afterlater.feature.capture

import com.afterlater.core.model.CaptureResult
import com.afterlater.test.FakeSavedItemRepository
import com.afterlater.test.MainDispatcherRule
import com.afterlater.test.savedItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QuickAddViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `an unexpected repository failure becomes a failed result`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeSavedItemRepository().apply {
                captureError = IllegalStateException("disk unavailable")
            }
            val viewModel = QuickAddViewModel(repository)

            viewModel.onInputChange("https://example.com/article")
            viewModel.save()
            advanceUntilIdle()

            val state = viewModel.state.value
            assertFalse(state.saving)
            assertNull(state.error)
            assertEquals(
                "The link could not be saved on this device. Try again.",
                (state.result as CaptureResult.Failed).reason,
            )
        }

    @Test
    fun `a successful capture is exposed to the screen`() =
        runTest(mainDispatcherRule.dispatcher) {
            val saved = savedItem()
            val repository = FakeSavedItemRepository().apply {
                captureResult = CaptureResult.Saved(saved)
            }
            val viewModel = QuickAddViewModel(repository)

            viewModel.onInputChange("https://example.com/article")
            viewModel.save()
            advanceUntilIdle()

            val state = viewModel.state.value
            assertFalse(state.saving)
            assertTrue(state.result is CaptureResult.Saved)
            assertEquals(saved, (state.result as CaptureResult.Saved).item)
        }
}
