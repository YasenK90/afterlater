package com.afterlater.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.afterlater.AfterLaterApplication
import com.afterlater.core.model.AppSettings
import com.afterlater.core.model.ReviewNotice
import com.afterlater.core.model.ReviewUndo
import com.afterlater.core.model.SavedItem
import com.afterlater.core.util.TimeFormat
import com.afterlater.data.repository.SavedItemRepository
import com.afterlater.domain.review.ReviewScheduler
import com.afterlater.domain.review.SnoozeWindow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant

data class TodayUiState(
    val loading: Boolean = true,
    val dueCount: Int = 0,
    val totalSaved: Int = 0,
    val featured: SavedItem? = null,
    val secondary: List<SavedItem> = emptyList(),
    val dailyLimit: Int = AppSettings.DEFAULT_DAILY_LIMIT,
    val now: Instant = Instant.now(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(
    private val repository: SavedItemRepository,
    private val settings: Flow<AppSettings>,
    private val clock: Clock = Clock.systemUTC(),
) : ViewModel() {

    private val refreshTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val messageChannel = Channel<ReviewNotice>(Channel.BUFFERED)
    val messages = messageChannel.receiveAsFlow()
    private var actionInFlight = false

    private val minuteTicker = flow {
        while (true) {
            emit(Unit)
            delay(60_000)
        }
    }

    private val triggers = merge(minuteTicker, refreshTrigger).onStart { emit(Unit) }

    val uiState: StateFlow<TodayUiState> = combine(
        settings,
        triggers,
    ) { settings, _ -> settings }
        .flatMapLatest { settings ->
            val now = clock.instant()
            combine(
                repository.observeDueCount(now.toEpochMilli()),
                repository.observeTotalCount(),
                repository.observeDueCandidates(now.toEpochMilli(), settings.dailyReviewLimit * 4),
            ) { dueCount, totalSaved, candidates ->
                val batch = ReviewScheduler.selectDailyBatch(
                    candidates = candidates,
                    limit = settings.dailyReviewLimit,
                    now = now,
                )
                TodayUiState(
                    loading = false,
                    dueCount = dueCount,
                    totalSaved = totalSaved,
                    featured = batch.firstOrNull(),
                    secondary = batch.drop(1),
                    dailyLimit = settings.dailyReviewLimit,
                    now = now,
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TodayUiState(),
        )

    fun open(item: SavedItem, launchLink: (String) -> Boolean) {
        if (!beginAction()) return
        if (!launchLink(item.originalUrl)) {
            endAction()
            messageChannel.trySend(ReviewNotice("NO APP CAN OPEN THIS LINK."))
            return
        }
        viewModelScope.launch {
            try {
                val change = repository.openItem(item.id)
                messageChannel.send(
                    if (change != null) {
                        ReviewNotice(
                            text = if (change.item.isReviewable) {
                                "OPENED — BACK IN 7 DAYS."
                            } else {
                                "OPENED. STILL OUT OF ROTATION."
                            },
                            undo = change.undo(),
                        )
                    } else {
                        ReviewNotice("THE ENTRY COULD NOT BE UPDATED.")
                    },
                )
                refresh()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                messageChannel.send(ReviewNotice("THE ENTRY COULD NOT BE UPDATED."))
            } finally {
                endAction()
            }
        }
    }

    fun snooze(item: SavedItem, window: SnoozeWindow) = perform("COULD NOT SNOOZE.") {
        val change = repository.snooze(item.id, window)
        val until = change?.item?.snoozedUntil
        if (until != null) {
            ReviewNotice("SNOOZED UNTIL ${TimeFormat.dateTime(until)}.", change?.undo())
        } else {
            ReviewNotice("THIS ENTRY IS NO LONGER IN ROTATION.")
        }
    }

    fun markUseful(item: SavedItem) = perform("COULD NOT MARK THIS ENTRY USEFUL.") {
        val change = repository.markUseful(item.id)
        if (change != null) {
            ReviewNotice("KEPT. IT LEAVES TODAY.", change.undo())
        } else {
            ReviewNotice("ENTRY NOT FOUND.")
        }
    }

    fun archive(item: SavedItem) = perform("COULD NOT ARCHIVE THIS ENTRY.") {
        val change = repository.archive(item.id)
        if (change != null) {
            ReviewNotice("ARCHIVED. STILL IN THE INDEX.", change.undo())
        } else {
            ReviewNotice("ENTRY NOT FOUND.")
        }
    }

    fun undo(undo: ReviewUndo) = perform("COULD NOT UNDO THAT ACTION.") {
        val change = repository.undoReview(undo)
        ReviewNotice(
            text = if (change != null) "RESTORED TO THE PREVIOUS STATE." else "ENTRY CHANGED. UNDO SKIPPED.",
        )
    }

    private fun perform(failure: String, block: suspend () -> ReviewNotice) {
        if (!beginAction()) return
        viewModelScope.launch {
            try {
                messageChannel.send(block())
                refresh()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                messageChannel.send(ReviewNotice(failure))
            } finally {
                endAction()
            }
        }
    }

    private fun beginAction(): Boolean {
        if (actionInFlight) return false
        actionInFlight = true
        return true
    }

    private fun endAction() {
        actionInFlight = false
    }

    fun refresh() {
        refreshTrigger.tryEmit(Unit)
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as AfterLaterApplication
                TodayViewModel(
                    repository = app.container.savedItemRepository,
                    settings = app.container.settingsRepository.settings,
                    clock = app.container.clock,
                )
            }
        }
    }
}
