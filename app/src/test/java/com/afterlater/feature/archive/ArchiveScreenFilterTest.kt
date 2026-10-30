package com.afterlater.feature.archive

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.afterlater.core.designsystem.AfterLaterTheme
import com.afterlater.core.model.ItemStatus
import com.afterlater.test.FakeSavedItemRepository
import com.afterlater.test.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class ArchiveScreenFilterTest {

    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `the in-rotation pill filters the repository query`() {
        val repository = FakeSavedItemRepository()
        val viewModel = ArchiveViewModel(repository)
        compose.setContent {
            AfterLaterTheme {
                ArchiveScreen(viewModel = viewModel, onOpenItem = {}, onSaveNew = {})
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("IN ROTATION").performClick()
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            setOf(ItemStatus.NEW, ItemStatus.READY, ItemStatus.SNOOZED),
            repository.lastArchiveQuery?.statuses,
        )
    }

    @Test
    fun `a second pill replaces the first filter`() {
        val repository = FakeSavedItemRepository()
        val viewModel = ArchiveViewModel(repository)
        compose.setContent {
            AfterLaterTheme {
                ArchiveScreen(viewModel = viewModel, onOpenItem = {}, onSaveNew = {})
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("USEFUL").performClick()
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
        compose.onNodeWithText("ARCHIVED").performClick()
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertEquals(setOf(ItemStatus.ARCHIVED), repository.lastArchiveQuery?.statuses)
    }

    @Test
    fun `the default query has no status filter and no tag`() {
        val repository = FakeSavedItemRepository()
        val viewModel = ArchiveViewModel(repository)
        compose.setContent {
            AfterLaterTheme {
                ArchiveScreen(viewModel = viewModel, onOpenItem = {}, onSaveNew = {})
            }
        }
        compose.waitForIdle()
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        val query = repository.lastArchiveQuery
        assertNull(query?.statuses)
        assertNull(query?.tag)
    }
}
