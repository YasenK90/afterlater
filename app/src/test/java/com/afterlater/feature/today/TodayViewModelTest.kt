package com.afterlater.feature.today

import com.afterlater.core.model.AppSettings
import com.afterlater.core.model.ItemStatus
import com.afterlater.core.model.MetadataStatus
import com.afterlater.core.model.SavedItem
import com.afterlater.test.FakeSavedItemRepository
import com.afterlater.test.MainDispatcherRule
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `new links stay out of Today until their first review is due`() =
        runTest(mainDispatcherRule.dispatcher) {
            val now = Instant.parse("2026-01-10T12:00:00Z")
            val clock = MutableClock(now)
            val item = item(
                id = "new-link",
                due = now.plusSeconds(3_600),
                savedAt = now.minusSeconds(23 * 3_600),
            )
            val repository = FakeSavedItemRepository().apply {
                dueItems.value = listOf(item)
                totalCount.value = 1
            }
            val viewModel = TodayViewModel(repository, flowOf(AppSettings()), clock)
            collect(viewModel)

            assertNull(viewModel.uiState.value.featured)

            clock.current = now.plusSeconds(3_600)
            viewModel.refresh()
            runCurrent()

            assertEquals("new-link", viewModel.uiState.value.featured?.id)
        }

    @Test
    fun `snoozed useful and archived links are excluded from Today`() =
        runTest(mainDispatcherRule.dispatcher) {
            val now = Instant.parse("2026-01-10T12:00:00Z")
            val clock = MutableClock(now)
            val repository = FakeSavedItemRepository().apply {
                dueItems.value = listOf(
                    item(
                        id = "snoozed",
                        due = now.minusSeconds(60),
                        status = ItemStatus.SNOOZED,
                        snoozedUntil = now.plusSeconds(3_600),
                    ),
                    // Useful and archived entries leave the rotation, so production
                    // gives them no next review instant unless a deadline pulls them back.
                    item(id = "useful", due = null, status = ItemStatus.USEFUL),
                    item(id = "archived", due = null, status = ItemStatus.ARCHIVED),
                    item(id = "ready", due = now.minusSeconds(60)),
                )
                totalCount.value = 4
            }
            val viewModel = TodayViewModel(repository, flowOf(AppSettings()), clock)
            collect(viewModel)

            assertEquals("ready", viewModel.uiState.value.featured?.id)
            assertEquals(1, viewModel.uiState.value.dueCount)
        }

    @Test
    fun `a snoozed link returns only after its pause expires`() =
        runTest(mainDispatcherRule.dispatcher) {
            val now = Instant.parse("2026-01-10T12:00:00Z")
            val clock = MutableClock(now)
            val repository = FakeSavedItemRepository().apply {
                dueItems.value = listOf(
                    item(
                        id = "snoozed",
                        due = now.minusSeconds(60),
                        status = ItemStatus.SNOOZED,
                        snoozedUntil = now.plusSeconds(3_600),
                    ),
                )
                totalCount.value = 1
            }
            val viewModel = TodayViewModel(repository, flowOf(AppSettings()), clock)
            collect(viewModel)

            assertNull(viewModel.uiState.value.featured)

            clock.current = now.plusSeconds(3_600)
            viewModel.refresh()
            runCurrent()

            assertEquals("snoozed", viewModel.uiState.value.featured?.id)
        }

    @Test
    fun `the candidate queue reloads when the repository changes`() =
        runTest(mainDispatcherRule.dispatcher) {
            val now = Instant.parse("2026-01-10T12:00:00Z")
            val repository = FakeSavedItemRepository()
            val viewModel = TodayViewModel(repository, flowOf(AppSettings()), MutableClock(now))
            collect(viewModel)

            assertNull(viewModel.uiState.value.featured)

            repository.dueItems.value = listOf(item(id = "arrived", due = now.minusSeconds(60)))
            repository.totalCount.value = 1
            runCurrent()

            assertEquals("arrived", viewModel.uiState.value.featured?.id)
        }

    @Test
    fun `the exposed clock is used for eligibility and display time`() =
        runTest(mainDispatcherRule.dispatcher) {
            val now = Instant.parse("2026-01-10T12:00:00Z")
            val clock = MutableClock(now)
            val repository = FakeSavedItemRepository().apply {
                dueItems.value = listOf(item(id = "due-now", due = now))
                totalCount.value = 1
            }
            val viewModel = TodayViewModel(repository, flowOf(AppSettings()), clock)
            collect(viewModel)

            assertEquals(now, viewModel.uiState.value.now)
            assertEquals("due-now", viewModel.uiState.value.featured?.id)
        }

    private fun kotlinx.coroutines.test.TestScope.collect(viewModel: TodayViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        runCurrent()
    }

    private fun item(
        id: String,
        due: Instant?,
        savedAt: Instant = due ?: Instant.EPOCH,
        status: ItemStatus = ItemStatus.NEW,
        snoozedUntil: Instant? = null,
    ) = SavedItem(
        id = id,
        originalUrl = "https://example.com/$id",
        canonicalUrl = "https://example.com/$id",
        title = id,
        description = null,
        domain = "example.com",
        thumbnailUrl = null,
        savedAt = savedAt,
        updatedAt = savedAt,
        status = status,
        lastReviewedAt = null,
        nextReviewAt = due,
        snoozedUntil = snoozedUntil,
        personalNote = null,
        metadataStatus = MetadataStatus.PENDING,
    )

    private class MutableClock(var current: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = current
    }
}
